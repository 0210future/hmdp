param(
    [Parameter(Mandatory = $true)]
    [string]$SourceMarkdown,
    [Parameter(Mandatory = $true)]
    [string]$OutputDocx
)

$ErrorActionPreference = "Stop"

function Escape-Xml([string]$Text) {
    if ($null -eq $Text) { return "" }
    return [System.Security.SecurityElement]::Escape($Text)
}

$buildRoot = Join-Path (Split-Path -Parent $SourceMarkdown) "_docx_build"
$zipPath = "$OutputDocx.zip"

if (Test-Path $buildRoot) {
    Remove-Item -LiteralPath $buildRoot -Recurse -Force
}

New-Item -ItemType Directory -Path $buildRoot | Out-Null
New-Item -ItemType Directory -Path (Join-Path $buildRoot "_rels") | Out-Null
New-Item -ItemType Directory -Path (Join-Path $buildRoot "word") | Out-Null

$lines = Get-Content -LiteralPath $SourceMarkdown -Encoding UTF8
$paragraphs = New-Object System.Collections.Generic.List[string]

foreach ($line in $lines) {
    $text = $line.TrimEnd()
    if ($text -match '^#\s+(.+)$') {
        $content = Escape-Xml $Matches[1]
        $paragraphs.Add("<w:p><w:pPr><w:pStyle w:val='Heading1'/></w:pPr><w:r><w:rPr><w:rFonts w:eastAsia='黑体'/><w:b/><w:sz w:val='32'/></w:rPr><w:t xml:space='preserve'>$content</w:t></w:r></w:p>")
    } elseif ($text -match '^##\s+(.+)$') {
        $content = Escape-Xml $Matches[1]
        $paragraphs.Add("<w:p><w:pPr><w:pStyle w:val='Heading2'/></w:pPr><w:r><w:rPr><w:rFonts w:eastAsia='黑体'/><w:b/><w:sz w:val='28'/></w:rPr><w:t xml:space='preserve'>$content</w:t></w:r></w:p>")
    } elseif ($text -match '^###\s+(.+)$') {
        $content = Escape-Xml $Matches[1]
        $paragraphs.Add("<w:p><w:pPr><w:pStyle w:val='Heading3'/></w:pPr><w:r><w:rPr><w:rFonts w:eastAsia='黑体'/><w:b/><w:sz w:val='26'/></w:rPr><w:t xml:space='preserve'>$content</w:t></w:r></w:p>")
    } elseif ($text -match '^####\s+(.+)$') {
        $content = Escape-Xml $Matches[1]
        $paragraphs.Add("<w:p><w:pPr><w:pStyle w:val='Heading4'/></w:pPr><w:r><w:rPr><w:rFonts w:eastAsia='黑体'/><w:b/><w:sz w:val='24'/></w:rPr><w:t xml:space='preserve'>$content</w:t></w:r></w:p>")
    } elseif ([string]::IsNullOrWhiteSpace($text)) {
        $paragraphs.Add("<w:p/>")
    } else {
        $plain = $text -replace '^\*\*(.+?)\*\*$', '$1'
        $content = Escape-Xml $plain
        $paragraphs.Add("<w:p><w:pPr><w:ind w:firstLineChars='200'/><w:spacing w:line='360' w:lineRule='auto'/></w:pPr><w:r><w:rPr><w:rFonts w:eastAsia='宋体'/><w:sz w:val='24'/></w:rPr><w:t xml:space='preserve'>$content</w:t></w:r></w:p>")
    }
}

$contentTypes = @"
<?xml version='1.0' encoding='UTF-8' standalone='yes'?>
<Types xmlns='http://schemas.openxmlformats.org/package/2006/content-types'>
  <Default Extension='rels' ContentType='application/vnd.openxmlformats-package.relationships+xml'/>
  <Default Extension='xml' ContentType='application/xml'/>
  <Override PartName='/word/document.xml' ContentType='application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml'/>
  <Override PartName='/word/styles.xml' ContentType='application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml'/>
</Types>
"@

$rels = @"
<?xml version='1.0' encoding='UTF-8' standalone='yes'?>
<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'>
  <Relationship Id='rId1' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument' Target='word/document.xml'/>
</Relationships>
"@

$styles = @"
<?xml version='1.0' encoding='UTF-8' standalone='yes'?>
<w:styles xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'>
  <w:style w:type='paragraph' w:default='1' w:styleId='Normal'>
    <w:name w:val='Normal'/>
    <w:qFormat/>
    <w:rPr><w:rFonts w:ascii='Times New Roman' w:eastAsia='宋体' w:hAnsi='Times New Roman'/><w:sz w:val='24'/></w:rPr>
  </w:style>
  <w:style w:type='paragraph' w:styleId='Heading1'>
    <w:name w:val='heading 1'/><w:basedOn w:val='Normal'/><w:qFormat/>
    <w:rPr><w:rFonts w:ascii='Times New Roman' w:eastAsia='黑体' w:hAnsi='Times New Roman'/><w:b/><w:sz w:val='32'/></w:rPr>
  </w:style>
  <w:style w:type='paragraph' w:styleId='Heading2'>
    <w:name w:val='heading 2'/><w:basedOn w:val='Normal'/><w:qFormat/>
    <w:rPr><w:rFonts w:ascii='Times New Roman' w:eastAsia='黑体' w:hAnsi='Times New Roman'/><w:b/><w:sz w:val='28'/></w:rPr>
  </w:style>
  <w:style w:type='paragraph' w:styleId='Heading3'>
    <w:name w:val='heading 3'/><w:basedOn w:val='Normal'/><w:qFormat/>
    <w:rPr><w:rFonts w:ascii='Times New Roman' w:eastAsia='黑体' w:hAnsi='Times New Roman'/><w:b/><w:sz w:val='26'/></w:rPr>
  </w:style>
  <w:style w:type='paragraph' w:styleId='Heading4'>
    <w:name w:val='heading 4'/><w:basedOn w:val='Normal'/><w:qFormat/>
    <w:rPr><w:rFonts w:ascii='Times New Roman' w:eastAsia='黑体' w:hAnsi='Times New Roman'/><w:b/><w:sz w:val='24'/></w:rPr>
  </w:style>
</w:styles>
"@

$documentXml = @"
<?xml version='1.0' encoding='UTF-8' standalone='yes'?>
<w:document xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'>
  <w:body>
    $($paragraphs -join "`n    ")
    <w:sectPr>
      <w:pgSz w:w='11906' w:h='16838'/>
      <w:pgMar w:top='1440' w:right='1800' w:bottom='1440' w:left='1800' w:header='851' w:footer='992' w:gutter='0'/>
    </w:sectPr>
  </w:body>
</w:document>
"@

$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText((Join-Path $buildRoot "[Content_Types].xml"), $contentTypes, $utf8NoBom)
[System.IO.File]::WriteAllText((Join-Path $buildRoot "_rels\.rels"), $rels, $utf8NoBom)
[System.IO.File]::WriteAllText((Join-Path $buildRoot "word\styles.xml"), $styles, $utf8NoBom)
[System.IO.File]::WriteAllText((Join-Path $buildRoot "word\document.xml"), $documentXml, $utf8NoBom)

if (Test-Path $OutputDocx) {
    Remove-Item -LiteralPath $OutputDocx -Force
}
if (Test-Path $zipPath) {
    Remove-Item -LiteralPath $zipPath -Force
}

Compress-Archive -Path (Join-Path $buildRoot "*") -DestinationPath $zipPath -Force
Rename-Item -LiteralPath $zipPath -NewName ([System.IO.Path]::GetFileName($OutputDocx))
Remove-Item -LiteralPath $buildRoot -Recurse -Force

Write-Output "CREATED: $OutputDocx"
