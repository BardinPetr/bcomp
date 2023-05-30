package ru.ifmo.cs.bcomp.assembler;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import java.util.List;

class AsmNGErrorListener extends BaseErrorListener {

    public List<String> errors;

    AsmNGErrorListener(List<String> errors) {
        this.errors = errors;
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
        StringBuilder sb = new StringBuilder();
        String symbol = offendingSymbol.toString();
        if (offendingSymbol instanceof org.antlr.v4.runtime.Token) {
            symbol = ((org.antlr.v4.runtime.Token) offendingSymbol).getText();
        }
        sb.append("at ").append(line).append(":").append(charPositionInLine);
        if (!("<EOF>".equalsIgnoreCase(symbol))) {
            sb.append(" near ").append(escapeWSAndQuote(symbol));
        }
        sb.append(" - ").append(msg);
        errors.add(sb.toString());
        //System.out.println("MY ERROR "+sb.toString());
    }

    protected String escapeWSAndQuote(String s) {
        if (s == null) {
            return s;
        }
        s = s.replace("\n", "\\n");
        s = s.replace("\r", "\\r");
        s = s.replace("\t", "\\t");
        return "'" + s + "'";
    }
}

