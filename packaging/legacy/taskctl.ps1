$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
$TaskArguments = @($args)
function Get-Sha256([string]$path) {
    $stream = [IO.File]::OpenRead($path)
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($algorithm.ComputeHash($stream)).Replace('-', '').ToLowerInvariant() }
    finally { $algorithm.Dispose(); $stream.Dispose() }
}
try {
    $lockFile = Join-Path $PSScriptRoot '.taskctl/toolchain.lock'
    $pin = @{}
    $allowed = @('lockFormat','toolVersion','wrapperVersion','adapter','windows-x86_64.url','windows-x86_64.sha256','linux-x86_64.url','linux-x86_64.sha256')
    foreach ($line in [IO.File]::ReadAllLines($lockFile)) {
        if ($line -eq '' -or $line.StartsWith('#')) { continue }
        if ($line -notmatch '^([^=]+)=(.*)$') { throw 'Malformed taskctl lock' }
        $key = $Matches[1]; $value = $Matches[2]
        if ($key -cnotin $allowed -or $pin.ContainsKey($key)) { throw "Unknown or duplicate lock key: $key" }
        $pin[$key] = $value
    }
    if ($pin.Count -ne $allowed.Count -or $pin.lockFormat -cne '1' -or $pin.wrapperVersion -cne '1') { throw 'Unsupported taskctl lock or wrapper version' }
    if ($env:PROCESSOR_ARCHITECTURE -ne 'AMD64') { throw 'This proof archive supports Windows x86_64' }
    $sha = $pin['windows-x86_64.sha256']
    if ($sha -cnotmatch '^[0-9a-f]{64}$') { throw 'Invalid locked checksum' }
    $url = [Uri]$pin['windows-x86_64.url']
    if ($url.Scheme -notin @('file','https')) { throw 'Only file or HTTPS artifact locations are accepted' }
    $cacheBase = if ($env:TASKCTL_CACHE) { $env:TASKCTL_CACHE } else { Join-Path $env:LOCALAPPDATA 'taskctl' }
    if (-not [IO.Path]::IsPathRooted($cacheBase)) { throw 'TASKCTL_CACHE must be absolute' }
    $cache = Join-Path $cacheBase $sha
    [IO.Directory]::CreateDirectory($cache) | Out-Null
    $archive = Join-Path $cache 'archive.zip'
    if (-not [IO.File]::Exists($archive)) {
        if ($env:TASKCTL_OFFLINE -eq '1') { throw 'Locked taskctl distribution is not cached (offline)' }
        $download = Join-Path $cache ('download-' + [Guid]::NewGuid().ToString('N'))
        try {
            if ($url.IsFile) { [IO.File]::Copy($url.LocalPath, $download) }
            else {
                [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
                $client = [Net.WebClient]::new()
                try { $client.DownloadFile($url, $download) } finally { $client.Dispose() }
            }
            if ((Get-Sha256 $download) -cne $sha) { throw 'taskctl distribution checksum mismatch' }
            try { [IO.File]::Move($download, $archive) } catch { if (-not [IO.File]::Exists($archive)) { throw } }
        } finally { if ([IO.File]::Exists($download)) { [IO.File]::Delete($download) } }
    }
    if ((Get-Sha256 $archive) -cne $sha) { throw 'Cached taskctl distribution checksum mismatch' }
    [void][Reflection.Assembly]::LoadWithPartialName('System.IO.Compression.FileSystem')
    $zip = [IO.Compression.ZipFile]::OpenRead($archive)
    try {
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName -notmatch '^[A-Za-z0-9_./+\-]+$' -or $entry.FullName.StartsWith('/') -or $entry.FullName.Split('/') -contains '..') { throw 'Unsafe archive path' }
        }
        $entry = $zip.GetEntry('files.sha256')
        if (-not $entry) { throw 'Distribution file manifest missing' }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $manifest = $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally { $zip.Dispose() }
    $install = Join-Path $cache 'home'
    if (-not [IO.Directory]::Exists($install)) {
        $staging = Join-Path $cache ('install-' + [Guid]::NewGuid().ToString('N'))
        [IO.Compression.ZipFile]::ExtractToDirectory($archive, $staging)
        try { [IO.Directory]::Move($staging, $install) }
        catch { if (-not [IO.Directory]::Exists($install)) { throw } }
        finally {
            # This is a generated child of the verified cache directory only.
            if ([IO.Directory]::Exists($staging)) { [IO.Directory]::Delete($staging, $true) }
        }
    }
    $libraryPaths = @()
    foreach ($line in ($manifest -split "`n")) {
        if ($line -eq '') { continue }
        if ($line -cnotmatch '^([0-9a-f]{64})  ([A-Za-z0-9_./+\-]+)$') { throw 'Malformed distribution file manifest' }
        $expected = $Matches[1]; $relative = $Matches[2]
        if ($relative.StartsWith('/') -or $relative.Split('/') -contains '..') { throw 'Unsafe manifest path' }
        $file = Join-Path $install $relative
        if ((Get-Sha256 $file) -cne $expected) { throw "Cached taskctl file checksum mismatch: $relative" }
        if ($relative.StartsWith('lib/') -and $relative.EndsWith('.jar')) { $libraryPaths += $file }
    }
    $distribution = [IO.File]::ReadAllLines((Join-Path $install 'distribution.properties'))
    if ($distribution -cnotcontains ('toolVersion=' + $pin.toolVersion) -or $distribution -cnotcontains ('adapter=' + $pin.adapter)) { throw 'Locked version or adapter does not match distribution' }
    $java = Join-Path $install 'runtime/bin/java.exe'
    $classpath = ($libraryPaths -join ';')
    # Windows PowerShell 5 does not preserve embedded quotes in native argument
    # arrays. Encode each argument using the Windows command-line rules.
    function ConvertTo-NativeArgument([string]$value) {
        $encoded = [Text.StringBuilder]::new()
        [void]$encoded.Append('"')
        $slashes = 0
        foreach ($character in $value.ToCharArray()) {
            if ($character -eq '\') { $slashes++; continue }
            if ($character -eq '"') {
                [void]$encoded.Append(('\' * (2 * $slashes + 1)))
                [void]$encoded.Append('"')
            } else {
                [void]$encoded.Append(('\' * $slashes))
                [void]$encoded.Append($character)
            }
            $slashes = 0
        }
        [void]$encoded.Append(('\' * (2 * $slashes)))
        [void]$encoded.Append('"')
        return $encoded.ToString()
    }
    $launchArguments = @('-cp', $classpath, 'io.brule.tasking.cli.MainKt', '--adapter', $pin.adapter) + $TaskArguments
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $java
    $start.UseShellExecute = $false
    $start.Arguments = ($launchArguments | ForEach-Object { ConvertTo-NativeArgument $_ }) -join ' '
    $process = [Diagnostics.Process]::Start($start)
    $process.WaitForExit()
    exit $process.ExitCode
} catch {
    [Console]::Error.WriteLine($_.Exception.Message)
    exit 2
}
