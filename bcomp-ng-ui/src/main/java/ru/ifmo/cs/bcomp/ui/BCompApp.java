/*
 * $Id$
 */

package ru.ifmo.cs.bcomp.ui;

import ru.ifmo.cs.bcomp.BasicComp;
import ru.ifmo.cs.bcomp.ProgramBinary;
import ru.ifmo.cs.bcomp.assembler.AsmNg;
import ru.ifmo.cs.bcomp.assembler.Program;
import ru.ifmo.cs.bcomp.assembler.binary.ExecutableGenerator;
import ru.ifmo.cs.components.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;

/**
 * @author Dmitry Afanasiev <KOT@MATPOCKuH.Ru>
 */
public class BCompApp {

	public static void main(String[] args) throws Exception {
		BasicComp bcomp = new BasicComp();
		String app;

		try {
			app = System.getProperty("mode", "dual");
		} catch (Exception e) {
			app = "dual";
		}

		if (app.equals("build")) {
			String path = System.getProperty("code", null);
			if (path == null) {
				System.err.println("No file supplied to build binary");
				System.exit(1);
			}
			try {
				var builder = new ExecutableGenerator();
				var binary = builder.process(path);
				System.out.println(binary.asStrings());
			} catch (Exception ex) {
				System.err.println(ex.getMessage());
			}
			System.exit(0);
		}

		try {
			String code = System.getProperty("code", null);
			File file = new File(code);

			try (var fin = new FileInputStream(file)) {
				byte[] content = fin.readAllBytes();
				code = new String(content, StandardCharsets.UTF_8);

				AsmNg asm = new AsmNg(code);
				Program pobj = asm.compile();
				if (asm.getErrors().isEmpty()) {
					ProgramBinary prog = new ProgramBinary(pobj.getBinaryFormat());

					bcomp.loadProgram(prog);
					System.out.printf("Program loaded. Instructions count: %d\n", pobj.content.size());

					if(System.getProperty("labels") != null) {
						System.out.println("labels in program: ");
						pobj.labels
								.values().stream()
								.sorted(Comparator.comparing(i -> i.name))
								.forEach(v -> System.out.printf(
												"%s: %s\n",
												v.name,
												Utils.toHex(v.address, 11)
										)
								);
					}
				} else {
					for (String err : asm.getErrors())
						System.out.println(err);
					System.exit(1);
				}
			}
		} catch (Exception e) { }

		try {
			String debuglevel = System.getProperty("debuglevel", "0");
			bcomp.getCPU().setDebugLevel(Long.parseLong(debuglevel));
		} catch (Exception e) { }

		if (app.equals("decoder")) {
			MicroCodeDecoder mpdecoder = new MicroCodeDecoder(bcomp);
			mpdecoder.decode();
			return;
		}

		bcomp.startTimer();

		if (app.equals("gui")) {
			GUI gui = new GUI(bcomp);
			gui.gui();
			return;
		}

		if (app.equals("cli")) {
			CLI cli = new CLI(bcomp);
			cli.cli();
			return;
		}

		if (app.equals("dual")) {
			CLI cli = new CLI(bcomp);
			GUI gui = new GUI(bcomp);
			gui.gui();
			cli.cli();
			return;
		}

		if (app.equals("nightmare")) {
			Nightmare nightmare = new Nightmare(bcomp);
			return;
		}

		System.err.println("Invalid mode selected");
	}
}
