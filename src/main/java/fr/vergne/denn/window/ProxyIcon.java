package fr.vergne.denn.window;

import static java.util.Objects.*;

import java.awt.Component;
import java.awt.Graphics;

import javax.swing.Icon;

public class ProxyIcon implements Icon {

	private Icon delegate;

	public ProxyIcon(Icon initialDelegate) {
		setDelegate(initialDelegate);
	}

	public Icon setDelegate(Icon delegate) {
		requireNonNull(delegate);
		Icon oldDelegate = this.delegate;
		this.delegate = delegate;
		return oldDelegate;
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
