param(
  [string]$Marker = 'MSDA_EVENT_WAKE_WATCHER'
)
# Marker is present on CommandLine for cleanup targeting only. Do NOT kill by AGENT_LOOP_WAKE.
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$logDir = 'd:\Programming\MSDA\docs\autonomous'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$debounceMs = 5000
$script:lastEmitMs = 0
$script:watchers = New-Object System.Collections.Generic.List[System.IO.FileSystemWatcher]
$script:sourceIds = New-Object System.Collections.Generic.List[string]
$script:regSeq = 0

function Emit-Wake([string]$promptText) {
  $now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
  if (($now - $script:lastEmitMs) -lt $debounceMs) { return }
  $script:lastEmitMs = $now
  $escaped = $promptText.Replace('\', '\\').Replace('"', '\"')
  $line = "AGENT_LOOP_WAKE_msda_dev {`"prompt`":`"$escaped`"}"
  [Console]::Out.WriteLine($line)
  [Console]::Out.Flush()
}

function Register-WakeWatcher([string]$path, [string]$filter) {
  if (-not (Test-Path -LiteralPath $path)) { return $false }
  $w = New-Object System.IO.FileSystemWatcher $path
  $w.Filter = $filter
  $w.IncludeSubdirectories = $false
  $w.NotifyFilter = [System.IO.NotifyFilters]'LastWrite, FileName, Size'
  $w.EnableRaisingEvents = $true
  [void]$script:watchers.Add($w)
  foreach ($ev in @('Changed', 'Created', 'Renamed')) {
    $script:regSeq++
    $sid = "MSDA_Wake_{0}_{1}_{2}" -f $script:regSeq, $ev, [guid]::NewGuid().ToString('N').Substring(0, 8)
    Register-ObjectEvent -InputObject $w -EventName $ev -SourceIdentifier $sid | Out-Null
    [void]$script:sourceIds.Add($sid)
  }
  return $true
}

[void](Register-WakeWatcher $logDir '*.md')

$transcriptRoots = @(
  'C:\Users\bohdanbtw\.cursor\projects\d-Programming-MSDA\agent-transcripts',
  'C:\Users\bohdanbtw\.cursor\projects\empty-window\agent-transcripts'
)
$subPaths = New-Object System.Collections.Generic.HashSet[string]
foreach ($root in $transcriptRoots) {
  if (-not (Test-Path -LiteralPath $root)) { continue }
  Get-ChildItem -LiteralPath $root -Recurse -Directory -Filter 'subagents' -ErrorAction SilentlyContinue | ForEach-Object {
    [void]$subPaths.Add($_.FullName)
  }
}
$subCount = 0
foreach ($sub in $subPaths) {
  if (Register-WakeWatcher $sub '*.jsonl') { $subCount++ }
}

$armedMsg = "MSDA event-wake armed marker=$Marker docs=$logDir subagents=$subCount"
[Console]::Out.WriteLine($armedMsg)
[Console]::Out.Flush()

$resumePrompt = 'Agent/docs output updated. Resume Architect, Boss, Worker if idle; continue MSDA development on branch development.'

try {
  while ($true) {
    $evt = Wait-Event -Timeout 60
    if ($null -eq $evt) { continue }
    try {
      Remove-Event -EventIdentifier $evt.EventIdentifier -ErrorAction SilentlyContinue
      Emit-Wake $resumePrompt
    } catch { }
  }
} finally {
  foreach ($sid in $script:sourceIds) {
    Unregister-Event -SourceIdentifier $sid -ErrorAction SilentlyContinue
    Remove-Event -SourceIdentifier $sid -ErrorAction SilentlyContinue
  }
  foreach ($w in $script:watchers) {
    try { $w.EnableRaisingEvents = $false; $w.Dispose() } catch { }
  }
}
