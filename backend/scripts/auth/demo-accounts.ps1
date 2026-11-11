param(
    [ValidateSet('check','apply','verify')][string]$Mode = 'check',
    [Parameter(Mandatory=$true)][string]$ExpectedServerUuid
)
$ErrorActionPreference = 'Stop'
$demoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$demoJar = Join-Path $demoRoot 'backend/target/backend-1.0-SNAPSHOT.jar'
$demoClasses = Join-Path $demoRoot 'backend/target/classes'
if (!(Test-Path -LiteralPath $demoJar) -or !(Test-Path -LiteralPath (Join-Path $demoClasses 'vn/edu/wspm/backend/config/auth/AuthConfiguration.class'))) {
    throw 'Build first: mvn --batch-mode --no-transfer-progress -pl backend -am -DskipTests package'
}
foreach ($demoSecretName in @('DEMO_ADMIN_PASSWORD','DEMO_CUSTOMER_PASSWORD')) {
    if (![Environment]::GetEnvironmentVariable($demoSecretName,'Process')) {
        $demoSecureValue = Read-Host "Enter $demoSecretName (not logged)" -AsSecureString
        $demoPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($demoSecureValue)
        try { [Environment]::SetEnvironmentVariable($demoSecretName,[Runtime.InteropServices.Marshal]::PtrToStringBSTR($demoPointer),'Process') }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($demoPointer) }
    }
}
$demoOldExpected = $env:DEMO_EXPECTED_SERVER_UUID
$demoOldScope = $env:DEMO_ACCOUNT_SCOPE
$env:DEMO_EXPECTED_SERVER_UUID = $ExpectedServerUuid
$env:DEMO_ACCOUNT_SCOPE = 'LOCAL_DEMO_ONLY'
$demoTemp = Join-Path ([IO.Path]::GetTempPath()) ('tv2-demo-accounts-' + [guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($demoTemp) | Out-Null
try {
    # Extract only dependency JARs to this invocation's own temp directory.
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $demoArchive = [IO.Compression.ZipFile]::OpenRead($demoJar)
    try {
        foreach ($demoEntry in $demoArchive.Entries) {
            if ($demoEntry.FullName -match '^BOOT-INF/lib/[^/]+\.jar$') {
                [IO.Compression.ZipFileExtensions]::ExtractToFile($demoEntry,(Join-Path $demoTemp $demoEntry.Name))
            }
        }
    } finally { $demoArchive.Dispose() }
    $demoClasspath = $demoClasses + [IO.Path]::PathSeparator + (Join-Path $demoTemp '*')
    & java -cp $demoClasspath (Join-Path $PSScriptRoot 'DemoAccounts.java') $Mode $demoRoot
    if ($LASTEXITCODE -ne 0) { throw "Demo account operation failed (exit $LASTEXITCODE)." }
} finally {
    $env:DEMO_EXPECTED_SERVER_UUID = $demoOldExpected
    $env:DEMO_ACCOUNT_SCOPE = $demoOldScope
    # Never touch a pre-existing directory: this unique directory was created above.
    $demoResolvedTemp = [IO.Path]::GetFullPath($demoTemp)
    $demoTempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    if ($demoResolvedTemp.StartsWith($demoTempRoot,[StringComparison]::OrdinalIgnoreCase) -and
            ([IO.Path]::GetFileName($demoResolvedTemp) -match '^tv2-demo-accounts-[a-f0-9]{32}$')) {
        Remove-Item -LiteralPath $demoResolvedTemp -Recurse -Force
    }
}
