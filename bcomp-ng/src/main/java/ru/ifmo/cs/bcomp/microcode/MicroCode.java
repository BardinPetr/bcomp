/*
 * $Id$
 */

package ru.ifmo.cs.bcomp.microcode;

import ru.ifmo.cs.bcomp.ControlSignal;
import ru.ifmo.cs.bcomp.State;

import static ru.ifmo.cs.bcomp.ControlSignal.*;
import static ru.ifmo.cs.bcomp.State.*;

/**
 * @author Dmitry Afanasiev <KOT@MATPOCKuH.Ru>
 */
public class MicroCode {
    private final omc[] MP = {
            // Halt basic computer when unnecessary call to reserved command or addressing type
            new omc(cs(HALT)),

            // Выборка команды
            new omc("INFETCH", cs(RDIP, HTOH, LTOL, WRAR, WRBR)),                          // IP -> AR, BR
            new omc(cs(RDBR, PLS1, HTOH, LTOL, WRIP, LOAD)),                    // BR + 1 -> IP, MEM(AR) -> DR
            new omc(cs(RDDR, HTOH, LTOL, WRCR)),                                // DR -> CR
            // Частичное декодирование
            new CMC(cs(RDCR, HTOL), 7, 1, "CHKBR"),   // if CR(15) = 1 then GOTO CHKBR
            new CMC(cs(RDCR, HTOL), 6, 1, "CHKABS"),  // if CR(14) = 1 then GOTO CHKABS
            new CMC(cs(RDCR, HTOL), 5, 1, "CHKABS"),  // if CR(13) = 1 then GOTO CHKABS
            new CMC(cs(RDCR, HTOL), 4, 0, "ADDRLESS"),// if CR(12) = 0 then GOTO ADDRLESS
            toLabel("IO"),      // GOTO IO
            new CMC("CHKBR", cs(RDCR, HTOL), 6, 0, "CHKABS"),  // if CR(14) = 0 then GOTO CHKABS
            new CMC(cs(RDCR, HTOL), 5, 0, "CHKABS"),  // if CR(13) = 0 then GOTO CHKABS
            new CMC(cs(RDCR, HTOL), 4, 1, "BRANCHES"),// if CR(12) = 1 then GOTO BRANCHES
            new CMC("CHKABS", cs(RDCR, HTOL), 3, 0, "OPFETCH"), // if CR(11) = 0 then GOTO OPFETCH

            // Выборка адреса
            /*
            0MMM $L
            1000 &N
            1001 (&N)
            1010 (&N)+
            1011 -(&N)
            1100 (L)
            1101 (L)+
            1110 (IP+N)
            1111 #N
             */
            new omc("ADFETCH", cs(RDCR, SEXT, LTOL, WRBR)),                                  // SEXT(CR) -> BR
            new CMC(cs(RDCR, HTOL), 2, 0, "ADR_SPREL"),  // if CR(2)=0 then GOTO SP-relative commands
            new CMC(cs(RDCR, HTOL), 1, 0, "ADR_IIPREL"),    // if CR(1)=0 then GOTO indirect IP-relative
            new CMC(cs(RDCR, HTOL), 0, 0, "ADR_DIPREL"),  // if CR(0)=0 then GOTO direct IP-relative

            // here CR(0)=1 - direct operand loading
            new omc("ADR_DIRLOAD", cs(RDBR, HTOH, LTOL, WRDR)),                                // BR -> DR
            toLabel("EXEC"),                                                                        // GOTO EXEC

            // direct relative
            new omc("ADR_DIPREL", cs(RDBR, RDIP, HTOH, LTOL, WRDR)),                          // BR + IP -> DR
            toLabel("OPFETCH"),                                                                    // GOTO OPFETCH

            // indirect IP-relative
            new omc("ADR_IIPREL", cs(RDBR, RDIP, HTOH, LTOL, WRAR)),                         // BR + IP  -> AR
            new omc(cs(LOAD)),                                                                     // MEM(AR) -> DR
            // indirect IP-relative normal - nothing to do
            new CMC(cs(RDCR, HTOL), 0, 0, "OPFETCH"),                       // if CR(0) = 0 then GOTO OPFETCH
            // indirect IP-relative with post-increment
            new omc("ADR_REL_POSTINC", cs(RDDR, PLS1, HTOH, LTOL, WRDR)),                   // DR + 1 -> DR
            new omc(cs(STOR)),                                                                    // DR -> MEM(AR)
            new omc(cs(RDDR, COML, HTOH, LTOL, WRDR)),                                            // DR - 1 -> DR
            toLabel("OPFETCH"),                                                                   // GOTO OPFETCH

            // SP-relative
            new CMC("ADR_SPREL", cs(RDCR, HTOL), 1, 1, "ADR_SPREL_IND"),
            new CMC(cs(RDCR, HTOL), 0, 1, "ADR_SPREL_IND"),
            // SP-relative direct
            new omc(cs(RDBR, RDSP, HTOH, LTOL, WRDR)),                                            // BR + SP -> DR
            toLabel("OPFETCH"),                                                                   // GOTO OPFETCH
            // SP-relative indirect ADR_SPREL_IND
            new omc("ADR_SPREL_IND", cs(RDBR, RDSP, HTOH, LTOL, WRAR)),                     // BR + SP -> AR
            new omc(cs(LOAD)),                                                                    // MEM(AR) -> DR
            // no inc/dec - go to opfetch
            new CMC(cs(RDCR, HTOL), 1, 0, "OPFETCH"),                       // if CR(1) = 0 then GOTO OPFETCH
            // reuse IP-relative postincrement final code for SP-relative
            new CMC(cs(RDCR, HTOL), 0, 0, "ADR_REL_POSTINC"),              // if CR(0) = 0 then GOTO ADR_REL_POSTINC
            // SP-relative indirect with predecrement
            new omc(cs(RDDR, COML, HTOH, LTOL, WRDR)),                                            // DR - 1 -> DR
            new omc(cs(STOR)),                                                                    // DR -> MEM(AR)
            // GOTO OPFETCH

            // Выборка операнда
            new CMC("OPFETCH", cs(RDCR, HTOL), 7, 0, "RDVALUE"), // if CR(15) = 0 then GOTO RDVALUE
            new CMC(cs(RDCR, HTOL), 6, 1, "CMD11XX"), // if CR(14) = 1 then GOTO CMD11XX
            new omc("RDVALUE", cs(RDDR, HTOH, LTOL, WRAR)),                                // DR -> AR
            new omc(cs(LOAD)),                                                  // MEM(AR) -> DR

            // Декодирование и цикл исполнения адресных команд кроме JUMP/CALL/ST/FXXX
            new CMC("EXEC", cs(RDCR, HTOL), 7, 1, "CMD1XXX"), // if CR(15) = 1 then GOTO CMD1XXX
            new CMC("CMD0XXX", cs(RDCR, HTOL), 6, 1, "CMD01XX"), // if CR(14) = 1 then GOTO CMD01XX
            // 13th bit already checked !!! CHECK LABEL NAME !!!
            new CMC("CMD000X", cs(RDCR, HTOL), 4, 1, "OR"),      // if CR(12) = 1 then GOTO OR
            new omc("AND", cs(RDAC, RDDR, SORA, HTOH, LTOL, STNZ, SETV, WRAC)),        // AC & DR -> AC, N, Z, V
            toINT(),     // GOTO INT
            new omc("OR", cs(RDAC, RDDR, COML, COMR, SORA, HTOH, LTOL, WRBR)),        // ~AC & ~DR -> BR
            new omc(cs(RDBR, COML, HTOH, LTOL, STNZ, SETV, WRAC)),              // ~BR -> AC, N, Z, V
            toINT(),     // GOTO INT
            new CMC("CMD01XX", cs(RDCR, HTOL), 5, 1, "CMD011X"), // if CR(13) = 1 then GOTO CMD011X
            new CMC("CMD010X", cs(RDCR, HTOL), 4, 1, "ADC"),     // if CR(12) = 1 then GOTO ADC
            new omc("ADD", cs(RDAC, RDDR, HTOH, LTOL, STNZ, SETV, SETC, WRAC)),        // AC + DR -> AC, N, Z, V, C
            toINT(),     // GOTO INT
            new CMC("ADC", cs(RDPS, LTOL), C.ordinal(), 0, "ADD"),     // if C = 0 then GOTO ADD
            new omc(cs(RDAC, RDDR, PLS1, HTOH, LTOL, STNZ, SETV, SETC, WRAC)),  // AC + DR + 1 -> BR, C, N, Z, V
            toINT(),     // GOTO INT
            new CMC("CMD011X", cs(RDCR, HTOL), 4, 1, "CMP"),     // if CR(12) = 1 then GOTO CMP
            new omc("SUB", cs(RDAC, RDDR, COMR, PLS1, HTOH, LTOL, STNZ, SETV, SETC, WRAC)),// AC + ~DR + 1 -> AC, N, Z, V, C
            toINT(),     // GOTO INT
            new omc("CMP", cs(RDAC, RDDR, COMR, PLS1, HTOH, LTOL, STNZ, SETV, SETC)),  // AC + ~DR + 1 -> N, Z, V, C
            toINT(),     // GOTO INT
            // Warning - 11XX was already checked
            new CMC("CMD1XXX", cs(RDCR, HTOL), 5, 1, "CMD101X"), // if CR(13) = 1 then GOTO CMD101X
            new CMC("CMD100X", cs(RDCR, HTOL), 4, 1, "RESERVED"),// if CR(12) = 1 then GOTO RESERVED (9000?)
            new omc("LOOP", cs(RDDR, COML, HTOH, LTOL, WRDR)),                          // DR + ~0 -> DR
            new omc(cs(STOR, RDDR, COML, HTOH, LTOL, WRBR)),                    // DR -> MEM(AR), DR + ~0 -> BR
            new CMC(cs(RDBR, HTOL), 7, 0, "INT"),     // if BR(15) = 0 then GOTO INT
            new omc(cs(RDIP, PLS1, HTOH, LTOL, WRIP)),                          // IP + 1 -> IP
            toINT(),     // GOTO INT

            // (FCALL)
            new CMC("CMD101X", cs(RDCR, HTOL), 4, 1, "CALL"),    // if CR(12) = 1 then GOTO CALL
            // !!! CHECK FLAGS !!!
            new omc("LD", cs(RDDR, HTOH, LTOL, STNZ, SETV, WRAC)),                    // DR -> AC, N, Z, V
            toINT(),     // GOTO INT

            // MUL (multiplication addressed command)
            /*
                // AC * DR = DR * (2^0 * AC[0] + 2^1 * AC[1] + ...) = (DR*1)*AC[0] + (DR*1*2)*AC[1]
                while DR != 0:
                    if AC[0] == 1:
                        BR = BR + DR
                    AC = ASR(AC)
                    DR *= 2
             */
//            new omc("MUL", cs(WRBR)), // 0 -> BR
//            new CMC("MUL_LOOP", cs(RDAC, LTOL), 0, 0, "MUL_X2"), // if AC[0] == 0 then GOTO MUL_LOOP_X2
//            new omc(cs(RDBR, RDDR, LTOL, HTOH, WRBR)), // DR + BR -> BR
//            new omc("MUL_X2", cs(RDDR, SHLT, STNZ, WRDR)), // ASL(DR) -> DR, Z, N
//            new omc(cs(RDAC, SHRT, SETC, STNZ, WRAC)), // ASR(AC) -> AC, N, Z
//            new CMC(cs(RDPS, LTOL), 2, 0, "MUL_LOOP"), // if Z == 0 then GOTO MUL_LOOP
//            new omc(cs(RDBR, LTOL, HTOH, WRAC)), // BR -> AC
//            toINT(),

            // ORIGINAL SWAM
//            new omc("SWAM", cs(RDDR, HTOH, LTOL, WRBR)),                                // DR -> BR
//            new omc(cs(RDAC, HTOH, LTOL, WRDR)),                                // AC -> DR
//            new omc(cs(RDBR, HTOH, LTOL, STNZ, SETV, WRAC, STOR)),              // DR -> MEM(AR), BR -> AC, N, Z, V
//            toINT(),     // GOTO INT

            // Warning - 1111 was already checked (addressless command)
            new CMC("CMD11XX", cs(RDCR, HTOL), 5, 1, "ST"),      // if CR(13) = 1 then GOTO ST
            new CMC("CMD110X", cs(RDCR, HTOL), 4, 1, "CALL"),    // if CR(12) = 1 then GOTO CALL
            new omc("JUMP", cs(RDDR, HTOH, LTOL, WRIP)),                                // DR -> IP
            toINT(),     // GOTO INT

            new omc("CALL", cs(RDDR, HTOH, LTOL, WRBR)),                                // DR -> BR
            new omc(cs(RDIP, HTOH, LTOL, WRDR)),                                // IP -> DR
            new omc(cs(RDBR, HTOH, LTOL, WRIP)),                                // BR -> IP
            new omc("PUSHVAL", cs(RDSP, COML, HTOH, LTOL, WRSP, WRAR)),                    // SP - 1 -> SP, AR
            toLabel("STORE"),   // GOTO STORE
            new omc("ST", cs(RDDR, HTOH, LTOL, WRAR)),                                // DR -> AR
            new omc(cs(RDAC, HTOH, LTOL, WRDR)),                                // AC -> DR
            new omc("STORE", cs(STOR)),                                                  // DR -> MEM(AR)
            toINT(),     // GOTO INT
            // Команды с "коротким" переходом
            new CMC("BRANCHES", cs(RDCR, HTOL), 3, 1, "BR1XXX"),  // if CR(11) = 1 then GOTO BR1XXX
            new CMC("BR0XXX", cs(RDCR, HTOL), 2, 1, "BR01XX"),  // if CR(10) = 1 then GOTO BR01XX
            new CMC("BR00XX", cs(RDCR, HTOL), 1, 1, "BR001X"),  // if CR(9) = 1 then GOTO BR001X
            new CMC("BR000X", cs(RDCR, HTOL), 0, 1, "BNE"),     // if CR(8) = 1 then GOTO BNE
            new CMC("BEQ", cs(RDPS, LTOL), Z.ordinal(), 0, "INT"),     // if Z = 0 then GOTO INT
            new omc("BR", cs(RDCR, SEXT, LTOL, WRBR)),                                // SEXT(CR) -> BR
            new omc(cs(RDBR, RDIP, HTOH, LTOL, WRIP)),                          // BR + IP -> IP
            toINT(),     // GOTO INT
            new CMC("BNE", cs(RDPS, LTOL), Z.ordinal(), 0, "BR"),      // if Z = 0 then GOTO BR
            toINT(),     // GOTO INT
            new CMC("BR001X", cs(RDCR, HTOL), 0, 1, "BPL"),     // if CR(8) then GOTO BPL
            new CMC("BMI", cs(RDPS, LTOL), N.ordinal(), 1, "BR"),      // if N = 1 then GOTO BR
            toINT(),     // GOTO INT
            new CMC("BPL", cs(RDPS, LTOL), N.ordinal(), 0, "BR"),      // if N = 0 then GOTO BR
            toINT(),     // GOTO INT
            new CMC("BR01XX", cs(RDCR, HTOL), 1, 1, "BR011X"),  // if CR(9) = 1 then GOTO BR011X
            new CMC("BR010X", cs(RDCR, HTOL), 0, 1, "BCC"),     // if CR(8) = 1 then GOTO BCC
            new CMC("BCS", cs(RDPS, LTOL), C.ordinal(), 1, "BR"),      // if C = 1 then GOTO BR
            toINT(),     // GOTO INT
            new CMC("BCC", cs(RDPS, LTOL), C.ordinal(), 0, "BR"),      // if C = 0 then GOTO BR
            toINT(),     // GOTO INT
            new CMC("BR011X", cs(RDCR, HTOL), 0, 1, "BVC"),     // if CR(8) = 1 then GOTO BCC
            new CMC("BVS", cs(RDPS, LTOL), V.ordinal(), 1, "BR"),      // if V = 1 then GOTO BR
            toINT(),     // GOTO INT
            new CMC("BVC", cs(RDPS, LTOL), V.ordinal(), 0, "BR"),      // if V = 0 then GOTO BR
            toINT(),     // GOTO INT
            new CMC("BR1XXX", cs(RDCR, HTOL), 2, 1, "IMM8OP"),// if CR(10) = 1 then GOTO IMM8OP (>= C)
            new CMC("BR10XX", cs(RDCR, HTOL), 1, 1, "RESERVED"),// if CR(9) = 1 then GOTO RESERVED (A, B)
            new CMC("BR100X", cs(RDCR, HTOL), 0, 1, "BGE"),     // if CR(8) = 1 then GOTO BGE
            new CMC("BLT", cs(RDPS, LTOL), N.ordinal(), 0, "BVS"),     // if N = 0 then GOTO BVS
            toLabel("BVC"),     // GOTO BVC
            new CMC("BGE", cs(RDPS, LTOL), N.ordinal(), 0, "BVC"),     // if N = 0 then GOTO BVC
            toLabel("BVS"),     // GOTO BVS

            // immediate 8bit operand commands
            new omc("IMM8OP", cs(RDCR, LTOL, SEXT, WRDR)),                            // SEXT(CR) -> DR
            new CMC(cs(RDCR, HTOL), 1, 1, "RESERVED"),               // if CR(9) = 1 then GOTO RESERVED (E, F)
            new CMC(cs(RDCR, HTOL), 0, 1, "RESERVED"),               // if CR(8) = 1 then GOTO RESERVED (D)
            new omc("SPADD", cs(RDSP, HTOH, LTOL, WRBR)),                             // SP -> BR
            new omc(cs(RDDR, RDBR, HTOH, LTOL, WRSP)),                                     // BR + DR -> SP
            toLabel("INT"),

            // Безадресные команды
            new CMC("ADDRLESS", cs(RDCR, HTOL), 3, 1, "AL1XXX"),  // if CR(11) = 1 then GOTO AL1XXX
            new CMC("AL0XXX", cs(RDCR, HTOL), 2, 1, "AL01XX"),  // if CR(10) = 1 then GOTO AL01XX
            new CMC("AL00XX", cs(RDCR, HTOL), 1, 1, "AL001X"),  // if CR(9) = 1 then GOTO AL001X
            new CMC("AL000X", cs(RDCR, HTOL), 0, 0, "INT"),     // if CR(8) = 0 then GOTO INT (NOP)
            new CMC("HLT", cs(RDPS, LTOL), PS0.ordinal(), 0, "STOP"),    // GOTO STOP
            new CMC("AL001X", cs(RDCR, HTOL), 0, 1, "AL0011"),  // if CR(8) = 1 then GOTO AL0011
            new CMC("AL0010", cs(RDCR, LTOL), 7, 1, "NOT"),     // if CR(7) = 1 then GOTO NOT
            new omc("CLA", cs(STNZ, SETV, WRAC)),                                      // 0 -> AC, N, V, Z
            toINT(),     // GOTO INT
            new omc("NOT", cs(RDAC, COML, HTOH, LTOL, STNZ, SETV, WRAC)),              // ~AC + 0 -> BR, N, Z, V
            toINT(),     // GOTO INT
            new CMC("AL0011", cs(RDCR, LTOL), 7, 1, "CMC"),     // if (CR7) = 1 then GOTO CMC
            new omc("CLC", cs(SETC)),                                                  // 0 -> C
            toINT(),     // GOTO INT
            new CMC("CMC", cs(RDPS, LTOL), C.ordinal(), 1, "CLC"),   // if C = 1 then GOTO CLC
            new omc(cs(COML, COMR, HTOH, SETC)),                                // 1 -> C
            toINT(),     // GOTO INT
            new CMC("AL01XX", cs(RDCR, HTOL), 1, 1, "AL011X"),  // if CR(9) = 1 then GOTO AL011X
            new CMC("AL010X", cs(RDCR, HTOL), 0, 1, "AL0101"),  // if CR(8) = 1 then GOTO AL0101
            new CMC("AL0100", cs(RDCR, LTOL), 7, 1, "ROR"),     // if CR(7) = 1 then GOTO ROR
            new omc("ROL", cs(RDAC, SHLT, SHL0, STNZ, SETV, SETC, WRAC)),              // ROL(AC) -> AC, N, Z, V, C
            toINT(),     // GOTO INT
            new omc("ROR", cs(RDAC, SHRT, SHRF, STNZ, SETV, SETC, WRAC)),              // ROR(AC) -> AC, N, Z, V, C
            toINT(),     // GOTO INT
            new CMC("AL0101", cs(RDCR, LTOL), 7, 1, "ASR"),     // if CR(7) = 1 then GOTO ASR
            new omc("ASL", cs(RDAC, HTOH, LTOL, WRDR)),                                // AC -> DR
            new omc(cs(RDAC, RDDR, HTOH, LTOL, STNZ, SETV, SETC, WRAC)),        // AC + DR -> AC, N, Z, V, C
            toINT(),     // GOTO INT
            new omc("ASR", cs(RDAC, SHRT, STNZ, SETV, SETC, WRAC)),                    // ASR(AC) -> AC, N, Z, V
            toINT(),     // GOTO INT
            new CMC("AL011X", cs(RDCR, HTOL), 0, 1, "AL0111"),  // if CR(8) = 1 then GOTO AL0111
            new CMC("AL0110", cs(RDCR, LTOL), 7, 1, "SWAB"),    // if CR(7) = 1 then GOTO SWAB
            new omc("SXTB", cs(RDAC, SEXT, LTOL, STNZ, SETV, WRAC)),                    // SEXT(AC) -> AC, N, Z, V
            toINT(),     // GOTO INT
            new omc("SWAB", cs(RDAC, HTOL, LTOH, STNZ, SETV, WRAC)),                    // SWAB(AC) -> AC, N, Z, V
            toINT(),     // GOTO INT
            new CMC("AL0111", cs(RDCR, LTOL), 7, 1, "NEG"),     // if CR(7) = 1 then GOTO NEG
            new CMC("AL01110", cs(RDCR, LTOL), 6, 1, "DEC"),     // if CR(6) = 1 then GOTO DEC
            new omc("INC", cs(RDAC, PLS1, HTOH, LTOL, STNZ, SETV, SETC, WRAC)),        // AC + 1 -> AC, N, Z, V, C
            toINT(),     // GOTO INT
            new omc("DEC", cs(RDAC, COMR, HTOH, LTOL, STNZ, SETV, SETC, WRAC)),        // AC + ~0 -> AC, N, Z, V, C
            toINT(),     // GOTO INT
            new omc("NEG", cs(RDAC, COML, PLS1, HTOH, LTOL, STNZ, SETV, SETC, WRAC)),  // ~AC + 1 -> AC, N, Z, V, C
            toINT(),     // GOTO INT
            new CMC("AL1XXX", cs(RDCR, HTOL), 2, 1, "AL11XX"),  // if CR(10) = 1 then AL11XX
            new omc("AL10XX", cs(RDSP, HTOH, LTOL, WRAR)),                                // SP -> AR
            new omc(cs(LOAD)),                                                  // MEM(AR) -> DR
            new CMC(cs(RDCR, HTOL), 1, 1, "AL101X"),  // if CR(9) = 1 then AL101X
            new CMC("AL100X", cs(RDCR, HTOL), 0, 1, "POPF"),    // if CR(8) = 1 then POPF
            new omc("POP", cs(RDDR, HTOH, LTOL, STNZ, SETV, WRAC)),                    // DR -> AC, N, Z, V
            new omc("INCSP", cs(RDSP, PLS1, HTOH, LTOL, WRSP)),                          // SP + 1 -> SP
            toINT(),     // GOTO INT
            new omc("POPF", cs(RDDR, HTOH, LTOL, WRPS)),                                // DR -> PS
            toLabel("INCSP"),   // GOTO INCSP
            new CMC("AL101X", cs(RDCR, HTOL), 0, 1, "IRET"),    // if CR(8) = 1 then GOTO IRET
            new omc("RET", cs(RDDR, HTOH, LTOL, WRIP)),                                // DR -> IP
            toLabel("INCSP"),   // GOTO INCSP
            new omc("IRET", cs(RDDR, HTOH, LTOL, WRPS)),                                // DR -> PS
            new omc(cs(RDSP, PLS1, HTOH, LTOL, WRSP, WRAR)),                    // SP + 1 -> SP, AR
            new omc(cs(LOAD)),                                                  // MEM(AR) -> DR
            toLabel("RET"),     // GOTO RET
            new CMC("AL11XX", cs(RDCR, HTOL), 1, 1, "AL111X"),  // if CR(9) = 1 then GOTO AL111X
            new CMC("AL110X", cs(RDCR, HTOL), 0, 1, "PUSHF"),   // if CR(8) = 1 then GOTO PUSHF
            new omc("PUSH", cs(RDAC, HTOH, LTOL, WRDR)),                                // AC -> DR
            toLabel("PUSHVAL"), // GOTO PUSHVAL
            new omc("PUSHF", cs(RDPS, HTOH, LTOL, WRDR)),                                // PS -> DR
            toLabel("PUSHVAL"), // GOTO PUSHVAL
            new CMC("AL111X", cs(RDCR, HTOL), 0, 1, "AL1111"),// if CR(8) = 1 then AL1111
            new omc("SWAP", cs(RDSP, HTOH, LTOL, WRAR)),                                // SP -> AR
            new omc(cs(LOAD)),                                                  // MEM(AR) -> DR
            new omc(cs(RDDR, HTOH, LTOL, WRBR)),                                // DR -> BR
            new omc(cs(RDAC, HTOH, LTOL, WRDR)),                                // AC -> DR
            new omc(cs(RDBR, HTOH, LTOL, STNZ, SETV, WRAC, STOR)),              // BR -> AC, N, Z, V; DR -> MEM(AR)
            toINT(),     // GOTO INT
            // 0FXX
            new CMC("AL1111", cs(RDCR, LTOL), 7, 0, "RESERVED"), // if CR(7) == 0 then RESERVED
            // 0 1111 1XXX X
            new CMC("AL11111", cs(RDCR, LTOL), 6, 0, "RSP"), // if CR(6) == 0 then RSP
            new omc("WSP", cs(RDAC, HTOH, LTOL, WRSP)), // AC -> SP
            toINT(),
            new omc("RSP", cs(RDSP, HTOH, LTOL, WRAC)), // SP -> AC
            toINT(),

            // IO
            new CMC("IO", cs(RDCR, HTOL), 3, 1, "IRQ"),     // if CR(11) = 1 then GOTO INT
            new omc("DOIO", cs(IO)),                                                    // IO

            // Цикл прерывания
            new CMC("INT", cs(RDPS, LTOL), W.ordinal(), 0, "STOP"),    // if RUN = 0 then GOTO STOP
            new CMC(cs(RDPS, LTOL), State.INT.ordinal(), 0, "INFETCH"), // if INTR = 0 then GOTO INFETCH
            new omc(cs(INTS)),                                                  // INT Sc
            new omc("IRQ", cs(RDSP, COML, HTOH, LTOL, WRSP, WRAR)),                    // SP + ~0 -> SP, AR
            new omc(cs(RDIP, HTOH, LTOL, WRDR)),                                // IP -> DR
            new omc(cs(STOR)),                                                  // DR -> MEM(AR)
            new omc(cs(RDSP, COML, HTOH, LTOL, WRSP, WRAR)),                    // SP + ~0 -> SP, AR
            new omc(cs(RDPS, HTOH, LTOL, WRDR)),                                // PS -> DR
            new omc(cs(RDCR, LTOL, WRBR, STOR)),                                // LTOL(CR) -> BR; DR -> MEM(AR)
            new omc(cs(RDBR, SHLT, WRBR, WRAR)),                                // 2 * BR -> BR, AR
            new omc(cs(LOAD)),                                                  // MEM(AR) -> DR
            new omc(cs(RDDR, HTOH, LTOL, WRIP)),                                // DR -> IP;
            new omc(cs(RDBR, PLS1, LTOL, WRAR)),                                // BR + 1 -> AR
            new omc(cs(LOAD)),                                                  // MEM(AR) -> DR
            new omc(cs(RDDR, HTOH, LTOL, WRPS)),                                // DR -> PS
            toLabel("INFETCH"), // GOTO INFETCH

            // Пуск
            new omc("START", cs(STNZ, SETV, SETC, WRDR, WRCR, WRSP, WRAC, WRBR, WRAR)),  // 0 -> all registers
            toLabel("DOIO"),    // GOTO DOIO
            // Чтение
            new omc("READ", cs(RDIP, HTOH, LTOL, WRAR)),                                // IP -> AR
            new omc(cs(RDIP, PLS1, HTOH, LTOL, WRIP, LOAD)),                    // MEM(AR) -> DR; IP + 1 -> IP
            toLabel("STOP"),    // GOTO STOP
            // Запись
            new omc("WRITE", cs(RDIP, HTOH, LTOL, WRAR)),                                // IP -> AR
            new omc(cs(RDIR, HTOH, LTOL, WRDR)),                                // IR -> DR
            new omc(cs(RDIP, PLS1, HTOH, LTOL, WRIP, STOR)),                    // DR -> MEM(AR); IP + 1 -> IP
            toLabel("STOP"),    // GOTO STOP
            // Ввод адреса
            new omc("SETIP", cs(RDIR, HTOH, LTOL, WRIP)),                                // IR -> IP
            // STOP
            new omc("STOP", cs(HALT)),                                                  // HALT
            toLabel("INFETCH"), // GOTO INFETCH

            // check 0FXX address-less commands // uncomment if needed
            // new CMC("AL1111", cs(RDCR, LTOL), 7, 1, "RESERVED"), // if CR(7) = 1 then GOTO RESERVED
            // new CMC(cs(RDCR, LTOL), 6, 1, "RESERVED"), // if CR(6) = 1 then GOTO RESERVED
            // new CMC(cs(RDCR, LTOL), 5, 1, "RESERVED"), // if CR(5) = 1 then GOTO RESERVED
            // new CMC(cs(RDCR, LTOL), 4, 1, "RESERVED"), // if CR(4) = 1 then GOTO RESERVED
            // new CMC(cs(RDCR, LTOL), 3, 1, "RESERVED"), // if CR(3) = 1 then GOTO RESERVED

            new omc("RESERVED", cs()),
            toINT() // ignore command
    };

    private static ControlSignal[] cs(ControlSignal... signals) {
        return signals;
    }

    public omc[] getMP() {
        return MP;
    }

    public int getMicroCodeLength() {
        return MP.length;
    }

    public long getMicroCommand(int addr) throws Exception {
        return MP[addr].getMicroCommand();
    }

    public int findLabel(String label) throws Exception {
        for (int addr = 0; addr < MP.length; addr++)
            if (label.equals(MP[addr].label))
                return addr;

        throw new Exception("Label '" + label + "' not found");
    }

    public String getLabel(int addr) {
        return addr < MP.length ? MP[addr].label : null;
    }

    private CMC toINT() {
        return toLabel("INT");
    }

    private CMC toLabel(String label) {
        return new CMCJump(null, label);
    }

    public class omc {
        public final String label;
        private final long microcmd;
        private final ControlSignal[] signals;

        public omc(String label, ControlSignal[] signals) {
            long microcmd = 0L;

            this.label = label;

            for (ControlSignal cs : (this.signals = signals)) {
                microcmd |= 1L << cs.ordinal();
            }

            this.microcmd = microcmd;
        }

        public omc(ControlSignal[] signals) {
            this(null, signals);
        }

        public long getMicroCommand() throws Exception {
            if (label != null)
                for (omc mc : MP)
                    if (this != mc)
                        if (label.equals(mc.label))
                            throw new Exception("Found duplicate label '" + label + "'");

            return microcmd;
        }
    }

    public class CMC extends omc {
        private final String labelto;
        private final long microcmd;

        public CMC(String label, ControlSignal[] signals, long startbit, long expected, String labelto) {
            super(label, signals);

            this.labelto = labelto;
            microcmd = (1L << TYPE.ordinal()) + (1L << (startbit + 16)) + (expected << 32);
        }

        public CMC(ControlSignal[] signals, long startbit, long expected, String labelto) {
            this(null, signals, startbit, expected, labelto);
        }

        @Override
        public long getMicroCommand() throws Exception {
            return microcmd | super.getMicroCommand() | (((long) findLabel(labelto)) << 24);
        }

        public String getLabelto() {
            return labelto;
        }
    }

    public class CMCJump extends CMC {

        public CMCJump(String label, String labelto) {
            super(label, cs(RDPS, LTOL), PS0.ordinal(), 0, labelto);
        }
    }
}
