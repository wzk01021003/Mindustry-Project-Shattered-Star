; Mindustry v160.7 + 碎星计划 Mod — Windows 安装程序
; 由 makensis 交叉编译，见 windows/build-exe.sh（构建脚本会自动转成 UTF-8 BOM）

Unicode true
!include "MUI2.nsh"

!ifndef STAGE_DIR
  !define STAGE_DIR "stage"
!endif
!ifndef OUT_FILE
  !define OUT_FILE "../dist/Mindustry-ShatteredStar-Setup.exe"
!endif

Name "Mindustry: 碎星计划 / Project Shattered Star"
OutFile "${OUT_FILE}"
InstallDir "$LOCALAPPDATA\Mindustry Shattered Star"
InstallDirRegKey HKCU "Software\Mindustry Shattered Star" "InstallDir"
RequestExecutionLevel user
SetCompressor /SOLID lzma
ManifestDPIAware true
ShowInstDetails hide
ShowUninstDetails hide

VIFileVersion "160.7.0.0"
VIProductVersion "160.7.0.0"
VIAddVersionKey /LANG=1033 "ProductName"     "Mindustry - Project Shattered Star"
VIAddVersionKey /LANG=1033 "FileDescription" "Mindustry v160.7 + Shattered Star Mod"
VIAddVersionKey /LANG=1033 "CompanyName"     "Anuken / A4PAPER (fan fork)"
VIAddVersionKey /LANG=1033 "FileVersion"     "160.7.0.0"
VIAddVersionKey /LANG=1033 "LegalCopyright"  "GPL-3.0; see LICENSE and NOTICE"

!define MUI_ICON "app.ico"
!define MUI_UNICON "app.ico"
!define MUI_ABORTWARNING
!define MUI_WELCOMEPAGE_TEXT "即将安装《Mindustry: 碎星计划 / Project Shattered Star》$\r$\n$\r$\n这是基于 Anuken/Mindustry v160.7（GPL-3.0）的非官方 fork，附带 A4PAPER 的「碎星计划」Java Mod，并内置 Java 17 运行时。$\r$\n$\r$\n游戏本体与官方桌面版完全一致；安装到当前用户目录，不需要管理员权限。"
!define MUI_DIRECTORYPAGE_TEXT_TOP "选择安装位置。首次启动时，自带的 Mod 会自动安装到「%APPDATA%\Mindustry\mods」；存档与设置也保存在 %APPDATA%\Mindustry，卸载不会删除存档。"
!define MUI_FINPAGE_RUN "$INSTDIR\Mindustry.exe"
!define MUI_FINPAGE_RUN_TEXT "立即启动游戏"
!define MUI_FINPAGE_TEXT "安装完成。$\r$\n$\r$\n桌面与开始菜单已创建「Mindustry 碎星计划」快捷方式。$\r$\n$\r$\n本作品基于 GPL-3.0 的 Mindustry 源码，随包附带 LICENSE、NOTICE、SOURCES.md；对外分发时请一并保留。"

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "SimpChinese"

Section "游戏本体" SEC_MAIN
  SectionIn RO
  SetOutPath "$INSTDIR"
  File /r "${STAGE_DIR}/*"

  WriteRegStr HKCU "Software\Mindustry Shattered Star" "InstallDir" "$INSTDIR"
  WriteRegStr   HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Mindustry Shattered Star" "DisplayName"     "Mindustry: 碎星计划 / Project Shattered Star"
  WriteRegStr   HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Mindustry Shattered Star" "DisplayIcon"     "$INSTDIR\Mindustry.exe"
  WriteRegStr   HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Mindustry Shattered Star" "UninstallString" "$INSTDIR\Uninstall.exe"
  WriteRegStr   HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Mindustry Shattered Star" "Publisher"       "Anuken / A4PAPER (fan fork)"
  WriteRegStr   HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Mindustry Shattered Star" "DisplayVersion"  "160.7"
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Mindustry Shattered Star" "NoModify"        1
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Mindustry Shattered Star" "NoRepair"        1

  WriteUninstaller "$INSTDIR\Uninstall.exe"

  CreateDirectory "$SMPROGRAMS\Mindustry 碎星计划"
  CreateShortCut "$SMPROGRAMS\Mindustry 碎星计划\Mindustry 碎星计划.lnk" "$INSTDIR\Mindustry.exe" ""          "$INSTDIR\Mindustry.exe" 0
  CreateShortCut "$SMPROGRAMS\Mindustry 碎星计划\调试模式.lnk"             "$INSTDIR\Mindustry.exe" "--console" "$INSTDIR\Mindustry.exe" 0
  CreateShortCut "$SMPROGRAMS\Mindustry 碎星计划\卸载.lnk"                 "$INSTDIR\Uninstall.exe"
  CreateShortCut "$DESKTOP\Mindustry 碎星计划.lnk"                          "$INSTDIR\Mindustry.exe" ""          "$INSTDIR\Mindustry.exe" 0
SectionEnd

Section "Uninstall"
  Delete "$DESKTOP\Mindustry 碎星计划.lnk"
  Delete "$SMPROGRAMS\Mindustry 碎星计划\Mindustry 碎星计划.lnk"
  Delete "$SMPROGRAMS\Mindustry 碎星计划\调试模式.lnk"
  Delete "$SMPROGRAMS\Mindustry 碎星计划\卸载.lnk"
  RMDir "$SMPROGRAMS\Mindustry 碎星计划"

  RMDir /r "$INSTDIR\jre"
  RMDir /r "$INSTDIR\mods"
  Delete "$INSTDIR\Mindustry.jar"
  Delete "$INSTDIR\Mindustry.exe"
  Delete "$INSTDIR\LICENSE"
  Delete "$INSTDIR\NOTICE"
  Delete "$INSTDIR\SOURCES.md"
  Delete "$INSTDIR\README-Windows.txt"
  Delete "$INSTDIR\Debug-Console.bat"
  Delete /REBOOTOK "$INSTDIR\Uninstall.exe"
  RMDir "$INSTDIR"

  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Mindustry Shattered Star"
  DeleteRegKey HKCU "Software\Mindustry Shattered Star"
SectionEnd
