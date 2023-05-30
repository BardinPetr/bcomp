/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.ifmo.cs.bcomp.assembler;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.antlr.v4.runtime.tree.TerminalNode;
import ru.ifmo.cs.bcomp.assembler.error.AssemblerAntlrErrorStrategy;
import ru.ifmo.cs.bcomp.assembler.error.AssemblerException;
import ru.ifmo.cs.bcomp.assembler.instructions.*;
import ru.ifmo.cs.bcomp.grammar.BCompNGParser;
import ru.ifmo.cs.bcomp.grammar.BCompNGParser.*;
import ru.ifmo.cs.bcomp.grammar.*;

import java.util.*;

/**
 * @author serge
 */
public class AsmNg {

    /**
     * If you don't have ORG directive assembler starts code generation from
     * base_address
     */
    public static final int BASE_ADDRESS = 0x10;
    private final CodePointCharStream program;
    private final BCompNGLexer lexer;
    private final CommonTokenStream tokens;
    private final BCompNGParser parser;
    private final AssemblerAntlrErrorStrategy errHandler;
    private final HashMap<String, Label> labels;
    private final HashMap<Integer, MemoryWord> memory;
    private final List<String> errors;

    protected AsmNg(CodePointCharStream program) {
        this.program = program;
        labels = new HashMap<String, Label>();
        memory = new HashMap<Integer, MemoryWord>();
        //
        lexer = new BCompNGLexer(program);
        tokens = new CommonTokenStream(lexer);
        this.parser = new BCompNGParser(tokens);
        errHandler = new AssemblerAntlrErrorStrategy();
        parser.setErrorHandler(errHandler);
        errors = new ArrayList<String>();
        ANTLRErrorListener lsnr = new AsmNGErrorListener(errors);
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        lexer.addErrorListener(lsnr);
        parser.addErrorListener(lsnr);
    }

    public AsmNg(String program) {
        //TODO fix grammar prog statement
        this(CharStreams.fromString(program + "\n"));
    }

    public static void main(String[] args) throws Exception {
        AsmNg asmng = new AsmNg(
                "ORG FF\n"
                        + "START: LOOP START\n"
                        + "LD   #FF\n"
                        + "IN \n"
                        + "ad: and ad\n"
                        + "ORG 030h\n"
                        + "    OR $ad\n"
                        + "bc:\n"
                        + "    WORD бяка\n"
                        + "    LD #0xFF\n"
                        + "    LD #-0x10\n"
                        + "    LD #0x-10\n"
                        + "    ST &0\n"
                        + "    ВЖУХ бяка\n"
                        + "eb:    WORD 44H,33,49,50\n"
                        + "бяка: WORD 22H\n"
                        + "    BR бяка\n"
                        + "    ПРЫГ (bc)\n"
                        + "    WORD 1 dup(-0x10)\n"
                        + "    WORD 0x12,?,0x13 ; komment\n");
        Program prog = asmng.compile();
        System.out.println("-------errors--------");
        System.out.println(asmng.getErrors());
        if (prog != null) {
            System.out.println("-------words--------");
            System.out.println(prog.toCompiledWords());
            System.out.println("-------binary--------");
            System.out.println(prog.toBinaryRepresentation());
        } else {
            System.out.println("Program is not compiled");
        }
    }

    private static Integer parseIntFromNumberContext(NumberContext nc, Parser parser) {
        Integer number;
        String text;
        if (nc.DECIMAL() != null) {
            text = nc.DECIMAL().getText();
            text = text.replaceAll("0[dD]", "");
            number = Integer.parseInt(text);
            return number;
        }
        if (nc.HEX() != null) {
            text = nc.HEX().getText();
            text = text.replaceAll("(0[xX])|[hH]", "");
            number = Integer.parseInt(text, 16);
            return number;
        }
        throw new AssemblerException("Could not recognize valid number while parsing " + nc.getText() + " operand", parser);
    }

    private static TerminalNode getTerminalNode(ParseTree p) {
        TerminalNode t = null;
        for (int i = 0; i < p.getChildCount(); i++) {
            ParseTree internal = p.getChild(i);
            if (internal instanceof TerminalNode) {
                t = (TerminalNode) internal;
                break;
            } else {
                t = getTerminalNode(internal);
                if (t != null) {
                    break;
                }
            }
        }
        return t;
    }

    public BCompNGParser getParser() {
        return parser;
    }

    public List<String> getErrors() {
        return errors;
    }

    public Program compile() {
        Program prog = null;
        try {
            //decode commands and collect all labels
            //System.out.println("first pass");
            firstPass();
            //debug output
            //System.out.println(labels);
            //System.out.println("second pass");
            prog = secondPass();
        } catch (AssemblerException e) {
            reportAndRecoverFromError(e);
        }
        return prog;
    }

    protected void firstPass() {
        RuleContext tree = getParser().prog();
        ParseTreeWalker walker = new ParseTreeWalker();
        BCompNGListener fp = new BCompNGBaseListener() {
            private int address = BASE_ADDRESS;

            @Override
            public void enterLine(BCompNGParser.LineContext ctx) {
                //verbose output for debug only
                //System.out.println("sourceline = "+ctx.getText());
            }

            @Override
            public void exitInstructionLine(BCompNGParser.InstructionLineContext ctx) {
                BCompNGParser.LblContext LCtx = ctx.lbl();
                Label label = null;
                if (LCtx != null) {
                    String labelname = LCtx.label().getText();
                    label = labels.get(labelname);
                }
                BCompNGParser.InstructionContext ICtx = ctx.instruction();
                if (ICtx != null) {
                    TerminalNode t = getTerminalNode(ICtx);
                    if (t == null) {
                        reportAndRecoverFromError(new AssemblerException("Internal error: TerminalNode occasionally is null", parser, ICtx));
                        return;
                    }
                    InstructionWord iw = new InstructionWord();
                    Instruction instr = instructionByParserType(t.getSymbol().getType());
                    if (instr == null) {
                        //parser has instruction description but ASM NG has not
                        //add new instruction to method instructionByParserType and Instruction
                        reportAndRecoverFromError(new AssemblerException("Internal error: Parser has instruction but assebler hasn't", parser, ICtx));
                        return;
                    }
                    iw.instruction = instr;
                    iw.address = address;
                    if (label != null) {
                        iw.label = label;  //labels can also be null by default
                    }
                    OperandContext OCtx = ICtx.operand();
                    if (OCtx != null)
                        iw.operand = addressingModeByParserContext(OCtx);

                    //process branches. Label in InstructionContext for now can only
                    //be in branches
                    if (instr.type == Instruction.Type.BRANCH && ICtx.label() != null) {
                        //make fake AddressingMode with references set up
                        iw.operand = new AddressedOperand();
                        //make String copy. Do not remove new String(..)
                        iw.operand.reference = ICtx.label().getText();
                    }
                    if (instr.type == Instruction.Type.IO) {
                        AssemblerException ae = new AssemblerException("Device or vector shall be valid number", parser, ICtx);
                        if (ICtx.dev() == null) {
                            reportAndRecoverFromError(ae);
                            return;
                        }
                        NumberContext nc = ICtx.dev().number();
                        if (nc == null) {
                            reportAndRecoverFromError(ae);
                            return;
                        }
                        iw.device = parseIntFromNumberContext(nc, parser);
                    }
                    if (instr.type == Instruction.Type.IMM) {
                        NumberContext nc = ICtx.directLoad().number();
                        if(nc == null) {
                            reportAndRecoverFromError(
                                    new AssemblerException(
                                            "Use of Imm8 instructions without number argument is prohibited",
                                            parser, ICtx
                                    )
                            );
                            return;
                        }
                        iw.operand = new AddressedOperand();
                        iw.operand.number = parseIntFromNumberContext(nc, parser);
                    }
                    if (instr.type == Instruction.Type.FAR) {
                        LabelContext lc = ICtx.indirectAbsolute().label();
                        if(lc == null) {
                            reportAndRecoverFromError(
                                    new AssemblerException(
                                            "Use of Far instructions without label operand is prohibited",
                                            parser, ICtx
                                    )
                            );
                            return;
                        }
                        iw.operand = new AddressedOperand();
                        iw.operand.reference = lc.getText();
                    }

                    memory.put(iw.address, iw);
                    address++;
                }
            }

            @Override
            public void exitWordArgument(WordArgumentContext ctx) {
                MemoryWord m = new MemoryWord();
                m.address = address;
                //parse direct numbers
                NumberContext nc = ctx.number();
                if (nc != null) {
                    Integer i = parseIntFromNumberContext(nc, parser);
                    m.value = i;
                }
                //undefined number will assume to 0
                if ("?".equals(ctx.getText())) {
                    m.value = 0;
                }
                LabelContext lc = ctx.label();
                if (lc != null) {
                    m.value_addr_reference = lc.getText();
                }
                //find out label if one and set it up to the first WORD
                if (ctx.getParent().getParent() instanceof WordDirectiveContext wdctx) {
                    //if label exsist in line
                    if (wdctx.lbl() != null) {
                        //look for this label address
                        Label l = labels.get(wdctx.lbl().label().getText());
                        if (l != null) {
                            // if label points to this first word instruction
                            if (l.address == address) {
                                m.label = l;
                            }
                        }
                    }
                }
                DupArgumentContext dactx = ctx.dupArgument();
                if (dactx != null) {
                    Integer count = parseIntFromNumberContext(dactx.count().number(), parser);
                    if (count <= 1) {
                        //throw new RuntimeException("Internal error: count should be greater than 1");
                        reportError(new AssemblerException("DUP count should be greater than 1", parser, dactx));
                        return;
                    }
                    WordArgumentContext what = dactx.wordArgument();
                    int whatnum = 0;
                    if (!"?".equals(what.getText())) {
                        whatnum = parseIntFromNumberContext(what.number(), parser);
                    }
                    //System.out.println("DUP="+count+" of "+whatnum);
                    for (int mm = 1; mm < count; mm++) {
                        MemoryWord dupm = new MemoryWord();
                        dupm.address = address++;
                        dupm.value = whatnum;
                        memory.put(dupm.address, dupm);
                    }
                    return;
                }

                memory.put(m.address, m);

                //System.out.println("WORD value = "+i);
                address++;
            }

            @Override
            public void exitLbl(LblContext ctx) {
                Label lab = new Label();
                //make String copy. Do not remove new String(..)
                lab.name = ctx.label().getText().trim();
                lab.address = address;
                if (labels.containsKey(lab.name)) {
                    //TODO FIX IT with common error message
                    reportAndRecoverFromError(new AssemblerException("Error: already defined label " + lab.name, parser, ctx));
                    return;
                }
                //TODO fix this special case for start label
                if ("START".equalsIgnoreCase(lab.name)) {
                    labels.put(lab.name, lab);
                    lab.name = "START";
                }
                labels.put(lab.name, lab);
            }

            @Override
            public void exitOrgAddress(OrgAddressContext ctx) {
                NumberContext n = ctx.address().number();
                Integer i = parseIntFromNumberContext(n, parser);
                address = i;
            }
        };
        walker.walk(fp, tree);
    }

    protected Program secondPass() {
        if (memory.keySet().isEmpty()) {
            //we need to stop compiling. Cant compile nothing
            AssemblerException ae = new AssemblerException("Second pass failed: no instruction was compiled on first pass.", parser);
            reportError(ae);
            return null;
        }
        LinkedList<Integer> addresses = new LinkedList<Integer>(memory.keySet());
        LinkedList<Integer> binary = new LinkedList<Integer>();
        Program prog = new Program();
        //
        Collections.sort(addresses);
        prog.load_address = addresses.getFirst();
        prog.start_address = prog.load_address;
        if (labels.containsKey("START")) {
            prog.start_address = labels.get("START").address;
        }
        int prev = addresses.getFirst();
        for (Integer addr : addresses) {
            MemoryWord w = memory.get(addr);
            if (w instanceof InstructionWord iw) {
                iw.value = iw.instruction.opcode;
                switch (iw.instruction.type) {
                    case NONADDR -> {}
                    case ADDR -> compileOperand(iw);
                    case BRANCH -> iw.setValueByOpcode(convertReferenceToDisplacement(iw));
                    case IO -> {
                        if (iw.instruction.opcode == Instruction.INT.opcode) {
                            if (iw.device < 0 || iw.device > 7) {
                                reportError(new AssemblerException("Second pass: vector exceed limits [0..7]", parser));
                            }
                            iw.setValueByOpcode(iw.device);
                            break;
                        }
                        if (iw.device < 0 || iw.device > 255) {
                            reportError(new AssemblerException("Second pass: device number exceed limits [0..0xff]", parser));
                        }
                        iw.setValueByOpcode(iw.device);
                    }
                    case IMM -> iw.setValueByOpcode(parseOneByteOperand(iw, -128, 255));
                    case FAR -> {
                        var lbl = labels.get(iw.operand.reference);
                        if (lbl == null) {
                            reportError(new AssemblerException("Second pass: label reference " + iw.operand.reference + " not found", parser));
                            break;
                        }
                        var lAddr = lbl.address;
                        if (lAddr < 0 || lAddr > MemoryWord.MAX_ADDRESS) {
                            reportError(new AssemblerException("Second pass: memory address 0x%X out of range [0..0x7FF]".formatted(lAddr), parser));
                            break;
                        }
                        iw.setValueByOpcode(lAddr);
                    }
                }
            }
            if (w.value_addr_reference != null) {
                Label l = labels.get(w.value_addr_reference);
                if (l == null) {
                    //TODO error
                    reportError(new AssemblerException("Second pass: Label " + w.value_addr_reference + " not found", parser));
                } else {
                    w.value = l.address;
                }
            }
            while (w.address - prev > 1) {
                //generate zeroes when hole found
                binary.add(0);
                prev++;
            }
            binary.add(w.value);
            prev = w.address; //to be sure
            //System.out.println(w);
        }
        prog.binary = binary;
        prog.labels = labels;
        prog.content = memory;
        return prog;
    }

    public final Instruction instructionByParserType(int parserType) {
        try {
            var realName = BCompNGParser.VOCABULARY.getSymbolicName(parserType);
            return Instruction.valueOf(realName);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return null;
        }
    }

    private AddressedOperand addressingModeByParserContext(OperandContext octx) {
        AddressedOperand am = new AddressedOperand();
        ParseTree pt = octx.getChild(0);
        if (pt == null | !(pt instanceof RuleContext)) {
            throw new AssemblerException("Internal error: after parser addressing mode cant be null and should be RuleContext", parser);
        }
        //System.out.println("!!!"+((RuleContext)pt).getRuleIndex());        
        switch (((RuleContext) pt).getRuleIndex()) {
            case BCompNGParser.RULE_directAbsolute -> {
                am.addressation = AddressedOperand.AddressingType.DIRECT_ABSOLUTE;
                DirectAbsoluteContext dactx = octx.directAbsolute();
                if (dactx.address() != null) {
                    am.number = parseIntFromNumberContext(dactx.address().number(), parser);
                }
                if (dactx.label() != null) {
                    am.reference = referenceByLabelContext(dactx.label());
                }
            }
            case BCompNGParser.RULE_indirectIP -> {
                am.addressation = AddressedOperand.AddressingType.INDIRECT_IP;
                am.reference = referenceByLabelContext(octx.indirectIP().label());
            }
            case BCompNGParser.RULE_indirectSP -> {
                am.addressation = AddressedOperand.AddressingType.INDIRECT_SP;
                am.number = parseIntFromNumberContext(octx.indirectSP().number(), parser);
            }
            case BCompNGParser.RULE_postIncrementIP -> {
                am.addressation = AddressedOperand.AddressingType.POST_INCREMENT_IP;
                am.reference = referenceByLabelContext(octx.postIncrementIP().label());
            }
            case BCompNGParser.RULE_postIncrementSP -> {
                am.addressation = AddressedOperand.AddressingType.POST_INCREMENT_SP;
                am.number = parseIntFromNumberContext(octx.postIncrementSP().number(), parser);
            }
            case BCompNGParser.RULE_preDecrementSP -> {
                am.addressation = AddressedOperand.AddressingType.PRE_DECREMENT_SP;
                am.number = parseIntFromNumberContext(octx.preDecrementSP().number(), parser);
            }
            case BCompNGParser.RULE_directRelativeSP -> {
                am.addressation = AddressedOperand.AddressingType.DIRECT_RELATIVE_SP;
                am.number = parseIntFromNumberContext(octx.directRelativeSP().number(), parser);
            }
            case BCompNGParser.RULE_directRelativeIP -> {
                am.addressation = AddressedOperand.AddressingType.DIRECT_RELATIVE_IP;
                am.reference = referenceByLabelContext(octx.directRelativeIP().label());
            }
            case BCompNGParser.RULE_directLoad -> {
                am.addressation = AddressedOperand.AddressingType.DIRECT_LOAD;
                am.number = parseIntFromNumberContext(octx.directLoad().number(), parser);
            }
            default ->
                    throw new AssemblerException("Internal error: Wrong OperandContext while parsing addressing mode", parser);
        }
        return am;
    }

    private String referenceByLabelContext(LabelContext lctx) {
        if (lctx == null) {
            AssemblerException ae = new AssemblerException("Internal error: LabelContex cant be null here", parser);
            reportError(ae);
        }
        //make String copy. Do not remove new String(..)
        return lctx.getText();
    }

    private void compileOperand(InstructionWord iw) {
        if (iw.operand == null) {
            return;
        }
        int num = MemoryWord.UNDEFINED;
        switch (iw.operand.addressation) {
            case DIRECT_ABSOLUTE -> {
                if (iw.operand.number != MemoryWord.UNDEFINED) {
                    num = iw.operand.number;
                }
                if (iw.operand.reference != null) {
                    Label l = labels.get(iw.operand.reference);
                    if (l == null)
                        reportError(new AssemblerException("Second pass: label reference " + iw.operand.reference + " not found", parser));
                    else
                        num = l.address;
                }
                if ((num > MemoryWord.MAX_ADDRESS) || (num < 0))
                    reportError(new AssemblerException("Second pass: memory address 0x" + Integer.toHexString(num) + " out of range [0..0x7FF]", parser));

                iw.setValueByOpcode(0, num & MemoryWord.MAX_ADDRESS);
            }

            case DIRECT_RELATIVE_SP -> iw.setValueByOpcode(0x0800, parseOneByteOperand(iw));
            case INDIRECT_SP -> iw.setValueByOpcode(0x0900, parseOneByteOperand(iw));
            case POST_INCREMENT_SP -> iw.setValueByOpcode(0x0A00, parseOneByteOperand(iw));
            case PRE_DECREMENT_SP -> iw.setValueByOpcode(0x0B00, parseOneByteOperand(iw));

            case INDIRECT_IP -> iw.setValueByOpcode(0x0C00, convertReferenceToDisplacement(iw));
            case POST_INCREMENT_IP -> iw.setValueByOpcode(0x0D00, convertReferenceToDisplacement(iw));
            case DIRECT_RELATIVE_IP -> iw.setValueByOpcode(0x0E00, convertReferenceToDisplacement(iw));

            case DIRECT_LOAD -> iw.setValueByOpcode(0x0F00, parseOneByteOperand(iw, -128, 255));
            default ->
                    reportError(new AssemblerException("Second pass: addressing mode is not properly defined", parser));
        }
    }

    private int parseOneByteOperand(InstructionWord iw) {
        return parseOneByteOperand(iw, -128, 127);
    }

    private int parseOneByteOperand(InstructionWord iw, int min, int max) {
        int num = MemoryWord.UNDEFINED;
        if (iw.operand.number != MemoryWord.UNDEFINED)
            num = iw.operand.number;
        else
            reportError(new AssemblerException("Second pass: number should present in command", parser));

        if (num > max || num < min) {
            reportError(new AssemblerException(
                    "Second pass: at addr 0x%x cmd %s operand %h exceed limits [%d..%d]".formatted(iw.address, iw.instruction.mnemonic, num, min, max),
                    parser
            ));
        }

        return 0xFF & ((byte) num);
    }

    private int convertReferenceToDisplacement(InstructionWord iw) {
        int num = MemoryWord.UNDEFINED;
        String reference = null;
        //address instructions
        if (iw.operand.reference != null) {
            reference = iw.operand.reference;
        }
        Label l = labels.get(reference);
        if (l == null) {
            AssemblerException ae = new AssemblerException("Second pass: label reference " + reference + " not found", parser);
            reportError(ae);
            return 0;
        }
        l.referenced = true;
        num = l.address - iw.address - 1; //-1 to fix impact of fetch cycle
        //TODO FIX
        if (num > 127 || num < -128) {
            AssemblerException ae = new AssemblerException("Second pass: label " + reference + " displacement exceed limits [-127..128]", parser);
            reportError(ae);
            num = 0;
        }
        return num & 0xFF;
    }

    private void reportError(AssemblerException ae) {
        errHandler.reportError(parser, ae);
    }

    private void reportAndRecoverFromError(AssemblerException ae) {
        errHandler.reportError(parser, ae);
        errHandler.recover(parser, ae);
    }
}