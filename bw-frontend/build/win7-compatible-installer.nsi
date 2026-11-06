; Win7 fallback installer: only NSIS built-in commands, no System.dll/UAC/StdUtils plugins.
Unicode true
RequestExecutionLevel user
; Payload is already compressed. Keeping NSIS storage avoids the old 32-bit compiler's
; memory-mapping limit when enumerating a multi-gigabyte uncompressed application.
SetCompress off
Name "${PRODUCT_NAME} (Win7 x86)"
OutFile "${INSTALLER_FILE}"
InstallDir "$LOCALAPPDATA\Programs\nip-traffic-system-win7"
InstallDirRegKey HKCU "Software\NipTrafficSystemWin7Compat" "InstallLocation"
ShowInstDetails show
ShowUninstDetails show
BrandingText "Windows x86 - Offline Integrated Package"
VIProductVersion "${APP_VERSION}.0"
VIAddVersionKey "ProductName" "${PRODUCT_NAME} Win7 x86"
VIAddVersionKey "FileDescription" "${PRODUCT_NAME} compatibility installer"
VIAddVersionKey "FileVersion" "${APP_VERSION}.0"
VIAddVersionKey "ProductVersion" "${APP_VERSION}.0"
VIAddVersionKey "LegalCopyright" "Wisdom, Inc"

Page directory
Page instfiles
UninstPage uninstConfirm
UninstPage instfiles

Section "Install"
  SetShellVarContext current
  ; Do not overwrite a running/mapped application executable.
  IfFileExists "$INSTDIR\${APP_EXE}" 0 files
  ClearErrors
  FileOpen $0 "$INSTDIR\${APP_EXE}" a
  IfErrors busy
  FileClose $0
  Goto files
busy:
  MessageBox MB_OK|MB_ICONSTOP "Please close the application and its error dialogs before installing."
  SetErrorLevel 2
  Abort
files:
  InitPluginsDir
  SetOutPath "$PLUGINSDIR"
  File /oname=extract.exe "${EXTRACTOR_EXE}"
  File /oname=application.7z "${PAYLOAD_ARCHIVE}"
  SetOutPath "$INSTDIR"
  SetOverwrite on
  ClearErrors
  ExecWait '"$PLUGINSDIR\extract.exe" x -y -bso0 -bsp0 "$PLUGINSDIR\application.7z" "-o$INSTDIR"' $0
  IfErrors installFailed
  IntCmp $0 0 extracted installFailed installFailed
extracted:
  WriteUninstaller "$INSTDIR\Uninstall-Win7-Compat.exe"
  WriteRegStr HKCU "Software\NipTrafficSystemWin7Compat" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\NipTrafficSystemWin7Compat" "DisplayName" "${PRODUCT_NAME} (Win7 x86 Compat)"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\NipTrafficSystemWin7Compat" "DisplayVersion" "${APP_VERSION}"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\NipTrafficSystemWin7Compat" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\NipTrafficSystemWin7Compat" "UninstallString" '"$INSTDIR\Uninstall-Win7-Compat.exe"'
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\NipTrafficSystemWin7Compat" "NoModify" 1
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\NipTrafficSystemWin7Compat" "NoRepair" 1
  CreateShortCut "$DESKTOP\${PRODUCT_NAME} (Win7 x86).lnk" "$INSTDIR\${APP_EXE}"
  CreateDirectory "$SMPROGRAMS\NipTrafficSystem Win7 x86"
  CreateShortCut "$SMPROGRAMS\NipTrafficSystem Win7 x86\${PRODUCT_NAME}.lnk" "$INSTDIR\${APP_EXE}"
  CreateShortCut "$SMPROGRAMS\NipTrafficSystem Win7 x86\Uninstall.lnk" "$INSTDIR\Uninstall-Win7-Compat.exe"
  CreateShortCut "$SMPROGRAMS\NipTrafficSystem Win7 x86\CH340 Driver.lnk" "$INSTDIR\drivers\ch340\install-driver.cmd"
  ; Keep the application per-user. Only the vendor driver installer requests elevation.
  IfSilent driverDone
  MessageBox MB_OK "Next, the CH340/CH341 driver installer will open. Allow administrator access, then click INSTALL. Reconnect the USB device after completion."
  ClearErrors
  ExecShell "runas" "$INSTDIR\drivers\ch340\SETUP.EXE" "" SW_SHOWNORMAL
  IfErrors driverLaunchFailed driverDone
driverLaunchFailed:
  MessageBox MB_OK|MB_ICONEXCLAMATION "Driver setup was not started. Use the CH340 Driver shortcut in the Start menu to retry."
driverDone:
  SetErrorLevel 0
  Goto complete
installFailed:
  MessageBox MB_OK|MB_ICONSTOP "Could not copy application files. Check disk space and directory permissions."
  SetErrorLevel 3
  Abort
complete:
SectionEnd

Section "Uninstall"
  SetShellVarContext current
  ; Only files included in this exact package are deleted. No recursive data deletion.
  !include "${UNINSTALL_FILES}"
  Delete "$INSTDIR\Uninstall-Win7-Compat.exe"
  RMDir "$INSTDIR"
  Delete "$DESKTOP\${PRODUCT_NAME} (Win7 x86).lnk"
  Delete "$SMPROGRAMS\NipTrafficSystem Win7 x86\${PRODUCT_NAME}.lnk"
  Delete "$SMPROGRAMS\NipTrafficSystem Win7 x86\Uninstall.lnk"
  Delete "$SMPROGRAMS\NipTrafficSystem Win7 x86\CH340 Driver.lnk"
  RMDir "$SMPROGRAMS\NipTrafficSystem Win7 x86"
  DeleteRegKey HKCU "Software\NipTrafficSystemWin7Compat"
  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\NipTrafficSystemWin7Compat"
SectionEnd
