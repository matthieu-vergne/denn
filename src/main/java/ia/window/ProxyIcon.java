package ia.window;

import java.awt.Component;
import java.awt.Graphics;

import javax.swing.Icon;

public class ProxyIcon implements Icon {

	private Icon delegate;

	public void setDelegate(Icon delegate) {
		this.delegate = delegate;
	}

	@Override
	public int getIconWidth() {
		return delegate.getIconWidth();
	}

	@Override
	public int getIconHeight() {
		return delegate.getIconHeight();
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y) {
		delegate.paintIcon(c, g, x, y);
	}
}
