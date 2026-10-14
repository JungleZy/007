; Preserve legacy mutable data BEFORE the old uninstaller removes install/bin.
; These hooks intentionally never kill a running application or remove snapshots.
!ifndef BUILD_UNINSTALLER
Var NipLegacyInstall
Var NipDataReceipt

!macro NipProtectData MODE
  ClearErrors
  ExecWait '"$SYSDIR\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "$PLUGINSDIR\nip-preserve-data.ps1" -Mode ${MODE} -InstallDirectory "$NipLegacyInstall" -TargetDirectory "$INSTDIR" -Receipt "$NipDataReceipt" -ApplicationExecutable "${APP_EXECUTABLE_FILENAME}"' $R0
  ${If} ${Errors}
  ${OrIf} $R0 != 0
    MessageBox MB_OK|MB_ICONSTOP "Cannot safely preserve user data. Close this application and its backend/file services, then retry. Legacy snapshots, when created, remain beside the old installation in .nip-data-backups. Installation stopped; do not delete those backups." /SD IDOK
    SetErrorLevel 1
    Quit
  ${EndIf}
!macroend

!macro customInit
  ; The shipped installer is per-user. A machine-wide legacy install needs a
  ; deliberate shared-data migration, not an arbitrary account's private profile.
  ReadRegStr $R0 HKLM "${INSTALL_REGISTRY_KEY}" InstallLocation
  ReadRegStr $R1 HKLM "${UNINSTALL_REGISTRY_KEY}" UninstallString
  ${If} $installMode != "CurrentUser"
  ${OrIf} $R0 != ""
  ${OrIf} $R1 != ""
    MessageBox MB_OK|MB_ICONSTOP "Machine-wide legacy installation detected. Back up shared data and arrange an administrator migration before using this per-user installer. Nothing has been uninstalled." /SD IDOK
    SetErrorLevel 1
    Quit
  ${EndIf}
  ReadRegStr $NipLegacyInstall HKCU "${INSTALL_REGISTRY_KEY}" InstallLocation
  ${If} $NipLegacyInstall == ""
    ReadRegStr $R1 HKCU "${UNINSTALL_REGISTRY_KEY}" UninstallString
    ${If} $R1 != ""
      Push $R1
      Call GetInQuotes
      Pop $R1
      ${If} $R1 == ""
        MessageBox MB_OK|MB_ICONSTOP "Cannot resolve the legacy uninstall location. Back up user data before repairing the installation registration." /SD IDOK
        SetErrorLevel 1
        Quit
      ${EndIf}
      ${StdUtils.GetParentPath} $NipLegacyInstall $R1
    ${Else}
      StrCpy $NipLegacyInstall $INSTDIR
    ${EndIf}
  ${EndIf}
  InitPluginsDir
  SetOutPath $PLUGINSDIR
  File /oname=nip-preserve-data.ps1 "${BUILD_RESOURCES_DIR}/windows/preserve-data.ps1"
  StrCpy $NipDataReceipt "$PLUGINSDIR\nip-data-receipt.json"
  !insertmacro NipProtectData Backup
!macroend

!macro customCheckAppRunning
  ; Recheck both process ownership and snapshot bytes immediately before removal.
  ; Do not use the stock taskkill fallback: it can leave detached services alive.
  !insertmacro NipProtectData Check
!macroend

!macro customUnInstallCheck
  ${If} ${Errors}
  ${OrIf} $R0 != 0
    MessageBox MB_OK|MB_ICONSTOP "The old uninstaller failed. Verified data snapshots were retained; installation stopped." /SD IDOK
    SetErrorLevel 1
    Quit
  ${EndIf}
!macroend

!macro customInstall
  !insertmacro NipProtectData Restore
!macroend
!endif
