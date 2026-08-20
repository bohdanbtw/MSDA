param(
  [string]$Marker = 'MSDA_EVENT_WAKE_WATCHER'
)
# $Marker appears on CommandLine for targeted cleanup only (never match AGENT_LOOP_WAKE).
$ErrorActionPreference = 'Continue'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$logDir = 'd:\Programming\MSDA\docs\autonomous'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$debounceMs = 5000
$script:lastEmitMs = 0

function Emit-Wake([string]$promptText) {
  $now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
  if (($now - $script:lastEmitMs) -lt $debounceMs) { return }
  $script:lastEmitMs = $now
  $escaped = $promptText.Replace('\', '\\').Replace('"', '\"')
  $line = "AGENT_LOOP_WAKE_msda_dev {`"prompt`":`"$escaped`"}"
  [Console]::Out.WriteLine($line)
  [Console]::Out.Flush()
}

$watchers = @()
$sourceIds = @()

function Register-WakeWatcher([string]$path, [string]$filter, [string]$idPrefix) {
  if (-not (Test-Path -LiteralPath $path)) { return $false }
  $w = New-Object System.IO.FileSystemWatcher $path
  $w.Filter = $filter
  $w.IncludeSubdirectories = $false
  $w.NotifyFilter = [System.IO.NotifyFilters]'LastWrite, FileName, Size'
  $w.EnableRaisingEvents = $true
  $script:watchers += $w
  foreach ($ev in @('Changed', 'Created', 'Renamed')) {
    $sid = "${idPrefix}_$ev"
    Register-ObjectEvent -InputObject $w -EventName $ev -SourceIdentifier $sid | Out-Null
    $script:sourceIds += $sid
  }
  return $true
}

[void](Register-WakeWatcher $logDir '*.md' 'MSDA_Wake_Docs')

$transcriptRoots = @(
  'C:\Users\bohdanbtw\.cursor\projects\d-Programming-MSDA\agent-transcripts',
  'C:\Users\bohdanbtw\.cursor\projects\empty-window\agent-transcripts'
)
$subFound = $false
foreach ($root in $transcriptRoots) {
  if (-not (Test-Path -LiteralPath $root)) { continue }
  $subs = @(Get-ChildItem -LiteralPath $root -Recurse -Directory -Filter 'subagents' -ErrorAction SilentlyContinue)
  foreach ($sub in $subs) {
    if (Register-WakeWatcher $sub.FullName '*.jsonl' ("MSDA_Wake_Sub_" + $sub.Parent.Name.Substring(0, [Math]::Min(8, $sub.Parent.Name.Length)))) {
      $subFound = $true
    }
  }
}

$armedMsg = "MSDA event-wake armed marker=$Marker docs=$logDir subagents=$subFound"
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
    } catch {
      # keep alive
    }
  }
} finally {
  foreach ($sid in $sourceIds) {
    Unregister-Event -SourceIdentifier $sid -ErrorAction SilentlyContinue
    Remove-Event -SourceIdentifier $sid -ErrorAction SilentlyContinue
  }
  foreach ($w in $watchers) {
    $w.EnableRaisingEvents = $false
    $w.Dispose()
  }
}
