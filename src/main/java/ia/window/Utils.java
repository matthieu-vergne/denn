package ia.window;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;

import javax.swing.JComponent;
import javax.swing.border.Border;

class Utils {
	static Dimension parentAvailableDimension(JComponent component) {
		JComponent parent = (JComponent) component.getParent();
		Rectangle parentBounds = parent.getBounds();
		Border border = parent.getBorder();
		Insets insets = border == null ? new Insets(0, 0, 0, 0) : border.getBorderInsets(parent);
		return new Dimension(//
				parentBounds.width - parentBounds.x - insets.left - insets.right, //
				parentBounds.height - parentBounds.y - insets.top - insets.bottom);
	}

}
