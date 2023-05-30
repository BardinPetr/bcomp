package ru.ifmo.cs.bcomp.ui.io;

import com.sshtools.terminal.emulation.VDUBuffer;
import com.sshtools.terminal.vt.awt.AWTTerminalPanel;
import ru.ifmo.cs.bcomp.io.IOCtrlDuplex;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

public class Terminal extends IODevice<IOCtrlDuplex> {
	private final Queue<Byte> outputQueue = new LinkedList<>();
	private VDUBuffer<AWTTerminalPanel> terminalBuffer;
	private JFileChooser fileChooser;

	public Terminal(IOCtrlDuplex ioctrl) {
		super(ioctrl, "terminal");
		System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");

		ioctrl.setDataHandler(this::onInput);
		ioctrl.setOnReady(this::onWriteReady);
	}

	@Override
	protected Component getContent() {
		return null;
	}

	@Override
	public void activate() {
		if (frame == null) {
			frame = new JFrame("Terminal");
			frame.setLayout(new BorderLayout());

			var display = new AWTTerminalPanel();

			terminalBuffer = display.getVDUBuffer();
			terminalBuffer.setInput((data, off, len) -> onTermData(data));
			terminalBuffer.setLocalEcho(false);
			terminalBuffer.setCharsetName("koi8-r");

//            frame.add(new AWTScrollBar(display), BorderLayout.EAST);

			fileChooser = new JFileChooser();
			fileChooser.setCurrentDirectory(new File(System.getProperty("user.dir")));

			var sendBtn = new JButton();
			sendBtn.setText("Send file");
			sendBtn.addActionListener(this::onSendFileClicked);
			frame.add(sendBtn, BorderLayout.SOUTH);

			frame.add(display, BorderLayout.CENTER);
			frame.pack();
		}

		frame.setVisible(true);
	}

	private void onSendFileClicked(ActionEvent actionEvent) {
		int res = fileChooser.showOpenDialog(getFrame());
		if (res == JFileChooser.APPROVE_OPTION) {
			try (var stream = new FileInputStream(fileChooser.getSelectedFile())) {
				onTermData(stream.readAllBytes());
			} catch (IOException e) {
				System.err.println("Failed to send file");
			}
		}
	}

	private void onTermData(byte[] data) {
		for (var i : data)
			outputQueue.add(i);

		if (!ioctrl.isReadAvailable())
			writeFromQueue();
	}

	private void writeFromQueue() {
		var data = outputQueue.poll();
		if (data == null) return;

		ioctrl.setData(data);
	}

	/**
	 * On received data from CPU
	 */
	private void onInput(long value) {
		if(terminalBuffer == null) return;

		try {
			terminalBuffer.write(new byte[]{(byte) value});
			terminalBuffer.flush();
			ioctrl.setWriteAvailable();
		} catch (IOException e) {
			System.err.printf("Term write failed: %s\n", e.getMessage());
		}
	}

	/**
	 * Called when CPU read from register
	 */
	private void onWriteReady(long x) {
		writeFromQueue();
	}
}
