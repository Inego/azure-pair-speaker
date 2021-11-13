import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.KeyStroke

fun JComponent.keyStrokeAction(keyChar: Char, actionKey: Any, block: () -> Unit) {
    inputMap.put(KeyStroke.getKeyStroke(keyChar), actionKey)
    actionMap.put(actionKey, object : AbstractAction() {
        override fun actionPerformed(e: ActionEvent?) = block()
    })
}
