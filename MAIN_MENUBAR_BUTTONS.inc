
<MenuItem name="MNU_USER" class="com.splwg.oms.jbot.component.MenuButton"
          icon="mc_down_arrow.svg" accelerator="ctrl alt U">
        <PressPerform>
            <Command value="DisplayPopupCommand">
                <Config name="popup" value="POPUP_USER"/>
            </Command>
        </PressPerform>
</MenuItem>
<Button name="BTN_ABOUT" class="com.splwg.oms.jbot.component.MenuButton">
    <ButtonPlacement/>
    <ButtonBehavior>
        <PressPerform>
            <Command value="GetClientToolBuildInfoCommand"/>
            <Command value="GetApplicationServerBuildInfoCommand"/>
            <Command value="DisplayDialogCommand">
                <Config name="dialog" value="DLG_ABOUT"/>
            </Command>
        </PressPerform>
    </ButtonBehavior>
</Button>
<Button name="MNU_TB_HELP" class="com.splwg.oms.jbot.component.MenuButton">
    <ButtonPlacement/>
    <ButtonBehavior text_horizontal_position="trailing" icon="mc_help.svg" accelerator="ctrl alt H">
        <PressPerform>
            <Command value="DisplayPopupCommand">
                <Config name="popup" value="POPUP_HELP"/>
            </Command>
        </PressPerform>
    </ButtonBehavior>
</Button>
