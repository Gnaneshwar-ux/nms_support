<Command value="custom.LoadCredentialsExternalCommand"/>
<Command value="CheckPasswordCommand" when="(DS_LOGIN_ENTRY.autoLogin)"/>
<Command value="PopulateWorkspacesCommand" when="(DS_LOGIN_ENTRY.autoLogin)"/>
<Command value="EnterSystemCommand" when="(DS_LOGIN_ENTRY.autoLogin)"/>
        