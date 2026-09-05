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
$classpath = ('__LIBRARIES__'.Split(';') | ForEach-Object { Join-Path $PSScriptRoot ('lib/' + $_) }) -join ';'
$launchArguments = @(('-Dtaskctl.distribution=' + $PSScriptRoot), '-cp', $classpath, 'io.brule.tasking.cli.MainKt') + $TaskArguments
$start = [Diagnostics.ProcessStartInfo]::new()
$start.FileName = Join-Path $PSScriptRoot 'runtime/bin/java.exe'
if ('__IMPLEMENTATION__' -eq 'native') {
    $start.FileName = Join-Path $PSScriptRoot 'taskctl.exe'
    $launchArguments = $TaskArguments
}
$start.UseShellExecute = $false
$start.EnvironmentVariables['TASKCTL_DISTRIBUTION'] = $PSScriptRoot
$start.Arguments = ($launchArguments | ForEach-Object { ConvertTo-NativeArgument $_ }) -join ' '
$process = [Diagnostics.Process]::Start($start)
$process.WaitForExit()
exit $process.ExitCode
