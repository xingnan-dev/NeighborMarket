[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:8088',
    [string]$Username = 'neighbor_a',
    [ValidateRange(0, 2147483647)] [int]$ExpectedMineMinimum = 0
)

$ErrorActionPreference = 'Stop'
$password = $env:SMOKE_PASSWORD
if ([string]::IsNullOrWhiteSpace($password)) {
    throw 'SMOKE_PASSWORD must be set in the process environment.'
}

$base = $BaseUrl.TrimEnd('/')
$script:session = $null

function Invoke-SmokeRequest {
    param(
        [Parameter(Mandatory)] [string]$Path,
        [ValidateSet('GET', 'POST')] [string]$Method = 'GET',
        [hashtable]$Headers = @{},
        [string]$Body
    )

    $arguments = @{
        Uri = "$base$Path"
        Method = $Method
        Headers = $Headers
        TimeoutSec = 15
        SkipHttpErrorCheck = $true
    }
    if ($null -eq $script:session) {
        $arguments.SessionVariable = 'createdSession'
    } else {
        $arguments.WebSession = $script:session
    }
    if ($PSBoundParameters.ContainsKey('Body')) {
        $arguments.ContentType = 'application/json'
        $arguments.Body = $Body
    }

    $response = Invoke-WebRequest @arguments
    if ($null -eq $script:session) {
        $script:session = $createdSession
    }
    if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 300) {
        throw "$Method $Path returned HTTP $($response.StatusCode)."
    }
    if ($Path -ne '/' -and [string]::IsNullOrWhiteSpace($response.Headers['X-Request-ID'])) {
        throw "$Method $Path did not return X-Request-ID."
    }
    return $response
}

function Get-SmokeContent {
    param([Parameter(Mandatory)] $Response)
    if ($Response.Content -is [byte[]]) {
        return [System.Text.Encoding]::UTF8.GetString($Response.Content)
    }
    return [string]$Response.Content
}

$homeResponse = Invoke-SmokeRequest -Path '/'
if ((Get-SmokeContent $homeResponse) -notmatch '<div id="app">') {
    throw 'Frontend entry page did not contain the Vue mount element.'
}

$health = Get-SmokeContent (Invoke-SmokeRequest -Path '/actuator/health') | ConvertFrom-Json
if ($health.status -ne 'UP') {
    throw "Actuator health was '$($health.status)' instead of 'UP'."
}

$csrf = Get-SmokeContent (Invoke-SmokeRequest -Path '/api/auth/csrf') | ConvertFrom-Json
if ([string]::IsNullOrWhiteSpace($csrf.token)) {
    throw 'CSRF endpoint did not return a token.'
}

$loginBody = @{ username = $Username; password = $password } | ConvertTo-Json -Compress
$login = Get-SmokeContent (Invoke-SmokeRequest -Path '/api/auth/login' -Method POST -Headers @{ 'X-XSRF-TOKEN' = $csrf.token } -Body $loginBody) | ConvertFrom-Json
if ($login.username -ne $Username) {
    throw 'Login response did not match the requested username.'
}

$me = Get-SmokeContent (Invoke-SmokeRequest -Path '/api/auth/me') | ConvertFrom-Json
if ($me.username -ne $Username -or [string]::IsNullOrWhiteSpace($me.id)) {
    throw 'Authenticated Session was not preserved by /api/auth/me.'
}

$market = Get-SmokeContent (Invoke-SmokeRequest -Path '/api/items?page=1&size=1') | ConvertFrom-Json
if ($null -eq $market.records -or $null -eq $market.total) {
    throw 'Core item list response did not contain the expected page fields.'
}

$mine = Get-SmokeContent (Invoke-SmokeRequest -Path '/api/items/mine?page=1&size=1') | ConvertFrom-Json
if ($null -eq $mine.records -or $null -eq $mine.total) {
    throw 'Authenticated item list did not contain the expected page fields.'
}
if ([int64]$mine.total -lt $ExpectedMineMinimum) {
    throw "Authenticated item count $($mine.total) was below expected minimum $ExpectedMineMinimum."
}

[pscustomobject]@{
    status = 'PASS'
    baseUrl = $base
    health = $health.status
    authenticatedUser = $me.username
    visibleItemCount = $market.total
    ownedItemCount = $mine.total
}
