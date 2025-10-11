
<MenuItem name="MNU_USER" class="com.splwg.oms.jbot.component.MenuButton"
          icon="mc_down_arrow.svg" accelerator="ctrl alt U">
        <PressPerform>
            <Command value="DisplayPopupCommand">
                <Config name="popup" value="POPUP_USER"/>
            </Command>
        </PressPerform>
</MenuItem>

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

<!-- Workspace Tools: open the Three Field sample dialog -->
<Button name="MNU_TB_THREE_FIELD_SAMPLE" class="com.splwg.oms.jbot.component.MenuButton">
    <ButtonPlacement/>
    <ButtonBehavior text_horizontal_position="trailing" icon="qual_form_16.svg" accelerator="ctrl alt T">
        <PressPerform>
            <Command value="DisplayDialogCommand">
                <Config name="dialog" value="DLG_THREE_FIELD_SAMPLE"/>
            </Command>
        </PressPerform>
    </ButtonBehavior>
</Button>
