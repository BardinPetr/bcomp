/*
 * $Id$
 */

package ru.ifmo.cs.bcomp.io.dev;

import ru.ifmo.cs.bcomp.io.IOCtrl;
import ru.ifmo.cs.bcomp.io.IOCtrlBasic;
import ru.ifmo.cs.components.Register;

/**
 *
 * @author Dmitry Afanasiev <KOT@MATPOCKuH.Ru>
 */
public class IODevTimer {
	private final IOCtrlBasic ctrl;
	private final Register dr;
	private Thread timer;
	private volatile boolean running = true;

	public IODevTimer(IOCtrl ctrl) {
		this.ctrl = (IOCtrlBasic)ctrl;
		this.dr = ctrl.getRegisters()[0];
	}

	public void start(String name) {
		timer = new Thread(() -> {
			long countdown = 0;
			long value;

			while (running) {
				try {
					 Thread.sleep(10);
				} catch (Exception ignored) { }

				value = dr.getValue();

				if (countdown != 0)
					if (countdown <= value) {
						if ((--countdown) == 0)
							ctrl.setReady();
						else
							continue;
					}

				countdown = value;
			}
		}, name);

		timer.start();
	}

	public void done() {
		running = false;

		try {
			timer.join();
		} catch (Exception ex) {
			System.out.println("Can't join thread: " + ex.getMessage());
		}
	}
}
