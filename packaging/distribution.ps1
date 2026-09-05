$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
$TaskArguments = @($args)
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
$launchArguments = @(('-Dtaskctl.distribution=' + $PSScriptRoot), '-cp', (Join-Path $PSScriptRoot 'lib/*'), 'io.brule.tasking.cli.MainKt') + $TaskArguments
$start = [Diagnostics.ProcessStartInfo]::new()
$start.FileName = Join-Path $PSScriptRoot 'runtime/bin/java.exe'
$start.UseShellExecute = $false
$start.Arguments = ($launchArguments | ForEach-Object { ConvertTo-NativeArgument $_ }) -join ' '
$process = [Diagnostics.Process]::Start($start)
$process.WaitForExit()
exit $process.ExitCode
