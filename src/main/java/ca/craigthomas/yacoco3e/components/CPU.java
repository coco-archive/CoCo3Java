/*
 * Copyright (C) 2017 Craig Thomas
 * This project uses an MIT style license - see LICENSE for details.
 */
package ca.craigthomas.yacoco3e.components;

import ca.craigthomas.yacoco3e.datatypes.*;

import java.util.function.Function;

/**
 * Implements an MC6809E microprocessor.
 */
public class CPU extends Thread
{
    /* CPU Internal Variables */
    private String opShortDesc;
    private String opLongDesc;
    private IOController io;

    /* Whether the CPU should be in a running state */
    private boolean alive;

    /* Whether trace output should occur */
    private boolean trace;

    /* Software Interrupt Vectors */
    public final static UnsignedWord SWI3 = new UnsignedWord(0xFFF2);
    public final static UnsignedWord SWI2 = new UnsignedWord(0xFFF4);
    public final static UnsignedWord SWI = new UnsignedWord(0xFFFA);

    /* Interrupt request flags */
    private boolean fireIRQ;
    private boolean fireFIRQ;
    private boolean fireNMI;

    public CPU(IOController io) {
        this.io = io;
        this.trace = false;
        this.alive = true;
    }

    /**
     * Sets the short description (for information purposes only) of the
     * currently executing instruction.
     *
     * @param string the message to print
     * @param value any memory value to print
     */
    public void setShortDesc(String string, MemoryResult value) {
        if (value != null) {
            opShortDesc = String.format(string, value.get().getInt());
        } else {
            opShortDesc = string;
        }
    }

    public void setTrace(boolean trace) {
        this.trace = trace;
    }

    /**
     * Executes the instruction as indicated by the operand. Will return the
     * total number of ticks taken to execute the instruction.
     *
     * @return the number of ticks taken up by the instruction
     */
    public int executeInstruction() throws IllegalIndexedPostbyteException {
        int operationTicks = 0;
        MemoryResult memoryResult = new MemoryResult();
        UnsignedByte operand = io.readByte(io.getWordRegister(Register.PC));
        if (trace) {
            System.out.print("PC " + io.getWordRegister(Register.PC) + ", operand " + operand + " : ");
        }
        io.incrementPC();
        int bytes;
        UnsignedWord tempWord;
        UnsignedByte a;
        UnsignedByte b;
        opShortDesc = "";
        opLongDesc = "";

        switch (operand.getShort()) {

            /* NEG - Negate M - Direct */
            case 0x00:
                memoryResult = io.getDirect();
                operationTicks = 6;
                executeByteFunctionM(this::negate, memoryResult);
                setShortDesc("NEGM, DIR [%04X]", memoryResult);
                break;

            /* COM - Complement M - Direct */
            case 0x03:
                memoryResult = io.getDirect();
                operationTicks = 6;
                executeByteFunctionM(this::compliment, memoryResult);
                setShortDesc("COMM, DIR [%04X]", memoryResult);
                break;

            /* LSR - Logical Shift Right - Direct */
            case 0x04:
                memoryResult = io.getDirect();
                operationTicks = 6;
                executeByteFunctionM(this::logicalShiftRight, memoryResult);
                setShortDesc("LSRM, DIR [%04X]", memoryResult);
                break;

            /* ROR - Rotate Right - Direct */
            case 0x06:
                memoryResult = io.getDirect();
                operationTicks = 6;
                executeByteFunctionM(this::rotateRight, memoryResult);
                setShortDesc("RORM, DIR [%04X]", memoryResult);
                break;

            /* ASR - Arithmetic Shift Right - Direct */
            case 0x07:
                memoryResult = io.getDirect();
                operationTicks = 6;
                executeByteFunctionM(this::arithmeticShiftRight, memoryResult);
                setShortDesc("ASRM, DIR [%04X]", memoryResult);
                break;

            /* ASL - Arithmetic Shift Left - Direct */
            case 0x08:
                memoryResult = io.getDirect();
                operationTicks = 6;
                executeByteFunctionM(this::arithmeticShiftLeft, memoryResult);
                setShortDesc("ASLM, DIR [%04X]", memoryResult);
                break;

            /* ROL - Rotate Left - Direct */
            case 0x09:
                memoryResult = io.getDirect();
                operationTicks = 6;
                executeByteFunctionM(this::rotateLeft, memoryResult);
                setShortDesc("ROLM, DIR [%04X]", memoryResult);
                break;

            /* DEC - Decrement - Direct */
            case 0x0A:
                memoryResult = io.getDirect();
                operationTicks = 6;
                executeByteFunctionM(this::decrement, memoryResult);
                setShortDesc("DECM, DIR [%04X]", memoryResult);
                break;

            /* INC - Increment - Direct */
            case 0x0C:
                memoryResult = io.getDirect();
                operationTicks = 6;
                executeByteFunctionM(this::increment, memoryResult);
                setShortDesc("INCM, DIR [%04X]", memoryResult);
                break;

            /* TST - Test - Direct */
            case 0x0D:
                memoryResult = io.getDirect();
                operationTicks = 6;
                executeByteFunctionM(this::test, memoryResult);
                setShortDesc("TSTM, DIR [%04X]", memoryResult);
                break;

            /* JMP - Jump - Direct */
            case 0x0E:
                memoryResult = io.getDirect();
                operationTicks = 3;
                jump(memoryResult.get());
                setShortDesc("JMP, DIR [%04X]", memoryResult);
                break;

            /* CLR - Clear - Direct */
            case 0x0F:
                memoryResult = io.getDirect();
                operationTicks = 6;
                executeByteFunctionM(this::clear, memoryResult);
                setShortDesc("CLRM, DIR [%04X]", memoryResult);
                break;

            /* 0x10 - Extended Opcodes */
            case 0x10:
            {
                UnsignedByte extendedOp = io.getPCByte();
                io.incrementPC();

                switch(extendedOp.getShort()) {

                    /* LBRN - Long Branch Never */
                    case 0x21:
                        memoryResult = io.getImmediateWord();
                        operationTicks = 5;
                        setShortDesc("LBRN, REL [%04X]", memoryResult);
                        break;

                    /* LBHI - Long Branch on Higher */
                    case 0x22:
                        memoryResult = io.getImmediateWord();
                        if (!io.ccCarrySet() && !io.ccZeroSet()) {
                            branchLong(memoryResult.get());
                            operationTicks = 6;
                        } else {
                            operationTicks = 5;
                        }
                        setShortDesc("LBHI, REL [%04X]", memoryResult);
                        break;

                    /* LBLS - Long Branch on Lower or Same */
                    case 0x23:
                        memoryResult = io.getImmediateWord();
                        if (io.ccCarrySet() || io.ccZeroSet()) {
                            branchLong(memoryResult.get());
                            operationTicks = 6;
                            opLongDesc = "C=" + io.ccCarrySet() + ", Z=" + io.ccZeroSet() + ", branching";
                        } else {
                            operationTicks = 5;
                            opLongDesc = "C=" + io.ccCarrySet() + ", Z=" + io.ccZeroSet() + ", not branching";
                        }
                        setShortDesc("LBLS, REL [%04X]", memoryResult);
                        break;

                    /* LBCC - Long Branch on Carry Clear */
                    case 0x24:
                        memoryResult = io.getImmediateWord();
                        if (!io.ccCarrySet()) {
                            branchLong(memoryResult.get());
                            operationTicks = 6;
                            opLongDesc = "C=0, PC=" + memoryResult.get();
                        } else {
                            operationTicks = 5;
                            opLongDesc = "C=1, not branching";
                        }
                        setShortDesc("LBCC, REL [%04X]", memoryResult);
                        break;

                    /* LBCS - Long Branch on Carry Set */
                    case 0x25:
                        memoryResult = io.getImmediateWord();
                        if (io.ccCarrySet()) {
                            branchLong(memoryResult.get());
                            operationTicks = 6;
                        } else {
                            operationTicks = 5;
                        }
                        setShortDesc("LBCS, REL [%04X]", memoryResult);
                        break;

                    /* LBNE - Long Branch on Not Equal */
                    case 0x26:
                        memoryResult = io.getImmediateWord();
                        if (!io.ccZeroSet()) {
                            branchLong(memoryResult.get());
                            operationTicks = 6;
                        } else {
                            operationTicks = 5;
                        }
                        setShortDesc("LBNE, REL [%04X]", memoryResult);
                        break;

                    /* LBEQ - Long Branch on Equal */
                    case 0x27:
                        memoryResult = io.getImmediateWord();
                        if (io.ccZeroSet()) {
                            branchLong(memoryResult.get());
                            operationTicks = 6;
                        } else {
                            operationTicks = 5;
                        }
                        setShortDesc("LBEQ, REL [%04X]", memoryResult);
                        break;

                    /* LBVC - Long Branch on Overflow Clear */
                    case 0x28:
                        memoryResult = io.getImmediateWord();
                        if (!io.ccOverflowSet()) {
                            branchLong(memoryResult.get());
                            operationTicks = 6;
                        } else {
                            operationTicks = 5;
                        }
                        setShortDesc("LBVC, REL [%04X]", memoryResult);
                        break;

                    /* LBVS - Long Branch on Overflow Set */
                    case 0x29:
                        memoryResult = io.getImmediateWord();
                        if (io.ccOverflowSet()) {
                            branchLong(memoryResult.get());
                            operationTicks = 6;
                        } else {
                            operationTicks = 5;
                        }
                        setShortDesc("LBVS, REL [%04X]", memoryResult);
                        break;

                    /* LBPL - Long Branch on Plus */
                    case 0x2A:
                        memoryResult = io.getImmediateWord();
                        if (!io.ccNegativeSet()) {
                            branchLong(memoryResult.get());
                            operationTicks = 6;
                        } else {
                            operationTicks = 5;
                        }
                        setShortDesc("LBPL, REL [%04X]", memoryResult);
                        break;

                    /* LBMI - Long Branch on Minus */
                    case 0x2B:
                        memoryResult = io.getImmediateWord();
                        if (io.ccNegativeSet()) {
                            branchLong(memoryResult.get());
                            operationTicks = 6;
                        } else {
                            operationTicks = 5;
                        }
                        setShortDesc("LBMI, REL [%04X]", memoryResult);
                        break;

                    /* LBGE - Long Branch on Greater Than or Equal to Zero */
                    case 0x2C:
                        memoryResult = io.getImmediateWord();
                        if (io.ccNegativeSet() == io.ccOverflowSet()) {
                            branchLong(memoryResult.get());
                            operationTicks = 6;
                        } else {
                            operationTicks = 5;
                        }
                        setShortDesc("LBGE, REL [%04X]", memoryResult);
                        break;

                    /* LBLT - Long Branch on Less Than or Equal to Zero */
                    case 0x2D:
                        memoryResult = io.getImmediateWord();
                        if (io.ccNegativeSet() != io.ccOverflowSet()) {
                            branchLong(memoryResult.get());
                            operationTicks = 6;
                        } else {
                            operationTicks = 5;
                        }
                        setShortDesc("LBLT, REL [%04X]", memoryResult);
                        break;

                    /* LBGT - Long Branch on Greater Than Zero */
                    case 0x2E:
                        memoryResult = io.getImmediateWord();
                        if (!io.ccZeroSet() && io.ccNegativeSet() == io.ccOverflowSet()) {
                            branchLong(memoryResult.get());
                            operationTicks = 6;
                        } else {
                            operationTicks = 5;
                        }
                        setShortDesc("LBGT, REL [%04X]", memoryResult);
                        break;

                    /* LBLE - Long Branch on Less Than Zero */
                    case 0x2F:
                        memoryResult = io.getImmediateWord();
                        if (io.ccZeroSet() || (io.ccNegativeSet() != io.ccOverflowSet())) {
                            branchLong(memoryResult.get());
                            operationTicks = 6;
                        } else {
                            operationTicks = 5;
                        }
                        setShortDesc("LBLE, REL [%04X]", memoryResult);
                        break;

                    /* SWI3 - Software Interrupt 3 */
                    case 0x3F:
                        softwareInterrupt(SWI3);
                        operationTicks = 19;
                        setShortDesc("SWI3", null);
                        break;

                    /* CMPD - Compare D - Immediate */
                    case 0x83:
                        memoryResult = io.getImmediateWord();
                        compareWord(io.getWordRegister(Register.D), memoryResult.get());
                        operationTicks = 5;
                        setShortDesc("CMPD, IMM [%04X]", memoryResult);
                        break;

                    /* CMPY - Compare Y - Immediate */
                    case 0x8C:
                        memoryResult = io.getImmediateWord();
                        compareWord(io.getWordRegister(Register.Y), memoryResult.get());
                        operationTicks = 5;
                        setShortDesc("CMPY, IMM [%04X]", memoryResult);
                        break;

                    /* LDY - Load Y - Immediate */
                    case 0x8E:
                        memoryResult = io.getImmediateWord();
                        loadRegister(Register.Y, memoryResult.get());
                        operationTicks = 4;
                        setShortDesc("LDY, IMM [%04X]", memoryResult);
                        break;

                    /* CMPD - Compare D - Direct */
                    case 0x93:
                        memoryResult = io.getDirect();
                        compareWord(io.getWordRegister(Register.D), io.readWord(memoryResult.get()));
                        operationTicks = 7;
                        setShortDesc("CMPD, DIR [%04X]", memoryResult);
                        break;

                    /* CMPY - Compare Y - Direct */
                    case 0x9C:
                        memoryResult = io.getDirect();
                        compareWord(io.getWordRegister(Register.Y), io.readWord(memoryResult.get()));
                        operationTicks = 7;
                        setShortDesc("CMPY, DIR [%04X]", memoryResult);
                        break;

                    /* LDY - Load Y - Direct */
                    case 0x9E:
                        memoryResult = io.getDirect();
                        loadRegister(Register.Y, io.readWord(memoryResult.get()));
                        operationTicks = 6;
                        setShortDesc("LDY, DIR [%04X]", memoryResult);
                        break;

                    /* STY - Store Y - Direct */
                    case 0x9F:
                        memoryResult = io.getDirect();
                        storeWordRegister(Register.Y, memoryResult.get());
                        operationTicks = 6;
                        setShortDesc("STY, DIR [%04X]", memoryResult);
                        break;

                    /* CMPD - Compare D - Direct */
                    case 0xA3:
                        memoryResult = io.getIndexed();
                        compareWord(io.getWordRegister(Register.D), io.readWord(memoryResult.get()));
                        operationTicks = 5 + memoryResult.getBytesConsumed();
                        setShortDesc("CMPD, IND [%04X]", memoryResult);
                        break;

                    /* CMPY - Compare Y - Direct */
                    case 0xAC:
                        memoryResult = io.getIndexed();
                        compareWord(io.getWordRegister(Register.Y), io.readWord(memoryResult.get()));
                        operationTicks = 5 + memoryResult.getBytesConsumed();
                        setShortDesc("CMPY, IND [%04X]", memoryResult);
                        break;

                    /* LDY - Load Y - Indexed */
                    case 0xAE:
                        memoryResult = io.getIndexed();
                        loadRegister(Register.Y, io.readWord(memoryResult.get()));
                        operationTicks = 4 + memoryResult.getBytesConsumed();
                        setShortDesc("LDY, IND [%04X]", memoryResult);
                        break;

                    /* STY - Store Y - Indexed */
                    case 0xAF:
                        memoryResult = io.getIndexed();
                        storeWordRegister(Register.Y, memoryResult.get());
                        operationTicks = 4 + memoryResult.getBytesConsumed();
                        setShortDesc("STY, IND [%04X]", memoryResult);
                        break;

                    /* CMPD - Compare D - Extended */
                    case 0xB3:
                        memoryResult = io.getExtended();
                        compareWord(io.getWordRegister(Register.D), io.readWord(memoryResult.get()));
                        operationTicks = 8;
                        setShortDesc("CMPD, EXT [%04X]", memoryResult);
                        break;

                    /* CMPY - Compare Y - Extended */
                    case 0xBC:
                        memoryResult = io.getExtended();
                        compareWord(io.getWordRegister(Register.Y), io.readWord(memoryResult.get()));
                        operationTicks = 8;
                        setShortDesc("CMPY, EXT [%04X]", memoryResult);
                        break;

                    /* LDY - Load Y - Extended */
                    case 0xBE:
                        memoryResult = io.getExtended();
                        loadRegister(Register.Y, io.readWord(memoryResult.get()));
                        operationTicks = 7;
                        setShortDesc("LDY, EXT [%04X]", memoryResult);
                        break;

                    /* STY - Store Y - Extended */
                    case 0xBF:
                        memoryResult = io.getExtended();
                        storeWordRegister(Register.Y, memoryResult.get());
                        operationTicks = 7;
                        setShortDesc("STY, EXT [%04X]", memoryResult);
                        break;

                    /* LDS - Load S - Immediate */
                    case 0xCE:
                        memoryResult = io.getImmediateWord();
                        loadRegister(Register.S, memoryResult.get());
                        operationTicks = 4;
                        setShortDesc("LDS, IMM [%04X]", memoryResult);
                        break;

                    /* LDS - Load S - Direct */
                    case 0xDE:
                        memoryResult = io.getDirect();
                        loadRegister(Register.S, io.readWord(memoryResult.get()));
                        operationTicks = 6;
                        setShortDesc("LDS, DIR [%04X]", memoryResult);
                        break;

                    /* STS - Store S - Direct */
                    case 0xDF:
                        memoryResult = io.getDirect();
                        storeWordRegister(Register.S, memoryResult.get());
                        operationTicks = 6;
                        setShortDesc("STS, DIR [%04X]", memoryResult);
                        break;

                    /* LDS - Load S - Indexed */
                    case 0xEE:
                        memoryResult = io.getIndexed();
                        loadRegister(Register.S, io.readWord(memoryResult.get()));
                        operationTicks = 4 + memoryResult.getBytesConsumed();
                        setShortDesc("LDS, IND [%04X]", memoryResult);
                        break;

                    /* STS - Store S - Indexed */
                    case 0xEF:
                        memoryResult = io.getIndexed();
                        storeWordRegister(Register.S, memoryResult.get());
                        operationTicks = 4 + memoryResult.getBytesConsumed();
                        setShortDesc("STS, IND [%04X]", memoryResult);
                        break;

                    /* LDS - Load S - Extended */
                    case 0xFE:
                        memoryResult = io.getExtended();
                        loadRegister(Register.S, io.readWord(memoryResult.get()));
                        operationTicks = 7;
                        setShortDesc("LDS, EXT [%04X]", memoryResult);
                        break;

                    /* STS - Store S - Extended */
                    case 0xFF:
                        memoryResult = io.getExtended();
                        storeWordRegister(Register.S, memoryResult.get());
                        operationTicks = 7;
                        setShortDesc("STS, EXT [%04X]", memoryResult);
                        break;
                }
                break;
            }

            /* 0x11 - Extended Opcodes */
            case 0x11: {
                UnsignedByte extendedOp = io.getPCByte();
                io.incrementPC();

                switch (extendedOp.getShort()) {
                    /* SWI2 - Software Interrupt 2 */
                    case 0x3F:
                        softwareInterrupt(SWI2);
                        operationTicks = 20;
                        setShortDesc("SWI2", null);
                        break;

                    /* CMPU - Compare U - Immediate */
                    case 0x83:
                        memoryResult = io.getImmediateWord();
                        compareWord(io.getWordRegister(Register.U), memoryResult.get());
                        operationTicks = 5;
                        setShortDesc("CMPU, IMM [%04X]", memoryResult);
                        break;

                    /* CMPS - Compare S - Immediate */
                    case 0x8C:
                        memoryResult = io.getImmediateWord();
                        compareWord(io.getWordRegister(Register.S), memoryResult.get());
                        operationTicks = 5;
                        setShortDesc("CMPS, IMM [%04X]", memoryResult);
                        break;

                    /* CMPU - Compare U - Direct */
                    case 0x93:
                        memoryResult = io.getDirect();
                        compareWord(io.getWordRegister(Register.U), io.readWord(memoryResult.get()));
                        operationTicks = 7;
                        setShortDesc("CMPU, DIR [%04X]", memoryResult);
                        break;

                    /* CMPS - Compare S - Direct */
                    case 0x9C:
                        memoryResult = io.getDirect();
                        compareWord(io.getWordRegister(Register.S), io.readWord(memoryResult.get()));
                        operationTicks = 7;
                        setShortDesc("CMPS, DIR [%04X]", memoryResult);
                        break;

                    /* CMPU - Compare U - Direct */
                    case 0xA3:
                        memoryResult = io.getIndexed();
                        compareWord(io.getWordRegister(Register.U), io.readWord(memoryResult.get()));
                        operationTicks = 5 + memoryResult.getBytesConsumed();
                        setShortDesc("CMPU, IND [%04X]", memoryResult);
                        break;

                    /* CMPS - Compare S - Direct */
                    case 0xAC:
                        memoryResult = io.getIndexed();
                        compareWord(io.getWordRegister(Register.S), io.readWord(memoryResult.get()));
                        operationTicks = 5 + memoryResult.getBytesConsumed();
                        setShortDesc("CMPS, IND [%04X]", memoryResult);
                        break;

                    /* CMPU - Compare U - Extended */
                    case 0xB3:
                        memoryResult = io.getExtended();
                        compareWord(io.getWordRegister(Register.U), io.readWord(memoryResult.get()));
                        operationTicks = 8;
                        setShortDesc("CMPU, EXT [%04X]", memoryResult);
                        break;

                    /* CMPS - Compare S - Extended */
                    case 0xBC:
                        memoryResult = io.getExtended();
                        compareWord(io.getWordRegister(Register.S), io.readWord(memoryResult.get()));
                        operationTicks = 8;
                        setShortDesc("CMPS, EXT [%04X]", memoryResult);
                        break;
                }
                break;
            }

            /* NOP - No Operation - Inherent */
            case 0x12:
                operationTicks = 2;
                setShortDesc("NOP", null);
                break;

            /* SYNC - Sync - Inherent */
            case 0x13:
                setShortDesc("SYNC", null);
                break;

            /* LBRA - Long Branch Always - Immediate */
            case 0x16:
                memoryResult = io.getImmediateWord();
                branchLong(memoryResult.get());
                operationTicks = 5;
                setShortDesc("LBRA, IMM [%04X]", memoryResult);
                break;

            /* LBSR - Long Branch to Subroutine */
            case 0x17:
                memoryResult = io.getImmediateWord();
                io.pushStack(Register.S, io.getWordRegister(Register.PC));
                branchLong(memoryResult.get());
                operationTicks = 9;
                setShortDesc("LBSR, IMM [%04X]", memoryResult);
                break;

            /* DAA - Decimal Addition Adjust */
            case 0x19:
                decimalAdditionAdjust();
                operationTicks = 2;
                setShortDesc("DAA, IMM [%04X]", memoryResult);
                break;

            /* ORCC - Logical OR on Condition Code Register */
            case 0x1A:
                memoryResult = io.getImmediateByte();
                opLongDesc = "CC=" + io.getByteRegister(Register.CC) + ", M=" + memoryResult.get().getHigh() + ", CC'=";
                io.getByteRegister(Register.CC).or(memoryResult.get().getHigh().getShort());
                opLongDesc += io.getByteRegister(Register.CC);
                operationTicks = 3;
                setShortDesc("ORCC, IMM [%04X]", memoryResult);
                break;

            /* ANDCC - Logical AND on Condition Code Register */
            case 0x1C:
                memoryResult = io.getImmediateByte();
                io.getByteRegister(Register.CC).and(memoryResult.get().getHigh().getShort());
                operationTicks = 3;
                setShortDesc("ANDCC, IMM [%04X]", memoryResult);
                break;

            /* SEX - Sign Extend */
            case 0x1D:
                io.setA(io.getByteRegister(Register.B).isMasked(0x80) ? new UnsignedByte(0xFF) : new UnsignedByte());
                operationTicks = 2;
                setShortDesc("SEX, IMM [%04X]", memoryResult);
                break;

            /* EXG - Exchange Register */
            case 0x1E: {
                memoryResult = io.getImmediateByte();
                UnsignedByte extendedOp = memoryResult.get().getHigh();
                UnsignedWord temp = new UnsignedWord();
                UnsignedByte tempByte = new UnsignedByte();
                setShortDesc("EXG, IMM [%04X]", memoryResult);
                operationTicks = 8;
                switch (extendedOp.getShort()) {

                    /* A:B <-> X */
                    case 0x01:
                    case 0x10:
                        temp.set(io.getWordRegister(Register.X));
                        io.setX(io.getWordRegister(Register.D));
                        io.setD(temp);
                        break;

                    /* A:B <-> Y */
                    case 0x02:
                    case 0x20:
                        temp.set(io.getWordRegister(Register.Y));
                        io.setY(io.getWordRegister(Register.D));
                        io.setD(temp);
                        break;

                    /* A:B <-> U */
                    case 0x03:
                    case 0x30:
                        temp.set(io.getWordRegister(Register.U));
                        io.setU(io.getWordRegister(Register.D));
                        io.setD(temp);
                        break;

                    /* A:B <-> S */
                    case 0x04:
                    case 0x40:
                        temp.set(io.getWordRegister(Register.S));
                        io.setS(io.getWordRegister(Register.D));
                        io.setD(temp);
                        break;

                    /* A:B <-> PC */
                    case 0x05:
                    case 0x50:
                        temp.set(io.getWordRegister(Register.PC));
                        io.setPC(io.getWordRegister(Register.D));
                        io.setD(temp);
                        break;

                    /* X <-> Y */
                    case 0x12:
                    case 0x21:
                        temp.set(io.getWordRegister(Register.X));
                        io.setX(io.getWordRegister(Register.Y));
                        io.setY(temp);
                        break;

                    /* X <-> U */
                    case 0x13:
                    case 0x31:
                        temp.set(io.getWordRegister(Register.X));
                        io.setX(io.getWordRegister(Register.U));
                        io.setU(temp);
                        break;

                    /* X <-> S */
                    case 0x14:
                    case 0x41:
                        temp.set(io.getWordRegister(Register.X));
                        io.setX(io.getWordRegister(Register.S));
                        io.setS(temp);
                        break;

                    /* X <-> PC */
                    case 0x15:
                    case 0x51:
                        temp.set(io.getWordRegister(Register.X));
                        io.setX(io.getWordRegister(Register.PC));
                        io.setPC(temp);
                        break;

                    /* Y <-> U */
                    case 0x23:
                    case 0x32:
                        temp.set(io.getWordRegister(Register.Y));
                        io.setY(io.getWordRegister(Register.U));
                        io.setU(temp);
                        break;

                    /* Y <-> S */
                    case 0x24:
                    case 0x42:
                        temp.set(io.getWordRegister(Register.S));
                        io.setS(io.getWordRegister(Register.Y));
                        io.setY(temp);
                        break;

                    /* Y <-> PC */
                    case 0x25:
                    case 0x52:
                        temp.set(io.getWordRegister(Register.Y));
                        io.setY(io.getWordRegister(Register.PC));
                        io.setPC(temp);
                        break;

                    /* U <-> S */
                    case 0x34:
                    case 0x43:
                        temp.set(io.getWordRegister(Register.U));
                        io.setU(io.getWordRegister(Register.S));
                        io.setS(temp);
                        break;

                    /* U <-> PC */
                    case 0x35:
                    case 0x53:
                        temp.set(io.getWordRegister(Register.U));
                        io.setU(io.getWordRegister(Register.PC));
                        io.setPC(temp);
                        break;

                    /* S <-> PC */
                    case 0x45:
                    case 0x54:
                        temp.set(io.getWordRegister(Register.S));
                        io.setS(io.getWordRegister(Register.PC));
                        io.setPC(temp);
                        break;

                    /* A <-> B */
                    case 0x89:
                    case 0x98:
                        tempByte.set(io.getByteRegister(Register.A));
                        io.setA(io.getByteRegister(Register.B));
                        io.setB(tempByte);
                        break;

                    /* A <-> CC */
                    case 0x8A:
                    case 0xA8:
                        tempByte.set(io.getByteRegister(Register.A));
                        io.setA(io.getByteRegister(Register.CC));
                        io.setCC(tempByte);
                        break;

                    /* A <-> DP */
                    case 0x8B:
                    case 0xB8:
                        tempByte.set(io.getByteRegister(Register.A));
                        io.setA(io.getByteRegister(Register.DP));
                        io.setDP(tempByte);
                        break;

                    /* B <-> CC */
                    case 0x9A:
                    case 0xA9:
                        tempByte.set(io.getByteRegister(Register.B));
                        io.setB(io.getByteRegister(Register.CC));
                        io.setCC(tempByte);
                        break;

                    /* B <-> DP */
                    case 0x9B:
                    case 0xB9:
                        tempByte.set(io.getByteRegister(Register.B));
                        io.setB(io.getByteRegister(Register.DP));
                        io.setDP(tempByte);
                        break;

                    /* CC <-> DP */
                    case 0xAB:
                    case 0xBA:
                        tempByte.set(io.getByteRegister(Register.CC));
                        io.setCC(io.getByteRegister(Register.DP));
                        io.setDP(tempByte);
                        break;

                    /* Self to self - ignored */
                    case 0x00:
                    case 0x11:
                    case 0x22:
                    case 0x33:
                    case 0x44:
                    case 0x55:
                    case 0x88:
                    case 0x99:
                    case 0xAA:
                    case 0xBB:
                        break;

                    default:
                        break;
                }
            }
            break;

            /* TFR - Transfer between registers */
            case 0x1F: {
                memoryResult = io.getImmediateByte();
                UnsignedByte extendedOp = memoryResult.get().getHigh();
                setShortDesc("TFR, IMM [%04X]", memoryResult);
                operationTicks = 6;
                switch (extendedOp.getShort()) {

                    /* A:B -> X */
                    case 0x01:
                        io.setX(io.getWordRegister(Register.D));
                        opLongDesc = "D->X, X'=" + io.getWordRegister(Register.X);
                        break;

                    /* A:B -> Y */
                    case 0x02:
                        io.setY(io.getWordRegister(Register.D));
                        opLongDesc = "D->Y, Y'=" + io.getWordRegister(Register.Y);
                        break;

                    /* A:B -> U */
                    case 0x03:
                        io.setU(io.getWordRegister(Register.D));
                        opLongDesc = "D->U, U'=" + io.getWordRegister(Register.U);
                        break;

                    /* A:B -> S */
                    case 0x04:
                        io.setS(io.getWordRegister(Register.D));
                        opLongDesc = "D->S, S'=" + io.getWordRegister(Register.S);
                        break;

                    /* A:B -> PC */
                    case 0x05:
                        io.setPC(io.getWordRegister(Register.D));
                        opLongDesc = "D->PC, PC'=" + io.getWordRegister(Register.PC);
                        break;

                    /* X -> A:B */
                    case 0x10:
                        io.setD(io.getWordRegister(Register.X));
                        opLongDesc = "X->D, D'=" + io.getWordRegister(Register.D);
                        break;

                    /* X -> Y */
                    case 0x12:
                        io.setY(io.getWordRegister(Register.X));
                        opLongDesc = "X->Y, Y'=" + io.getWordRegister(Register.Y);
                        break;

                    /* X -> U */
                    case 0x13:
                        io.setU(io.getWordRegister(Register.X));
                        opLongDesc = "X->U, U'=" + io.getWordRegister(Register.U);
                        break;

                    /* X -> S */
                    case 0x14:
                        io.setS(io.getWordRegister(Register.X));
                        opLongDesc = "X->S, S'=" + io.getWordRegister(Register.S);
                        break;

                    /* X -> PC */
                    case 0x15:
                        io.setPC(io.getWordRegister(Register.X));
                        opLongDesc = "X->PC, PC'=" + io.getWordRegister(Register.PC);
                        break;

                    /* Y -> A:B */
                    case 0x20:
                        io.setD(io.getWordRegister(Register.Y));
                        opLongDesc = "Y->D, D'=" + io.getWordRegister(Register.D);
                        break;

                    /* Y -> X */
                    case 0x21:
                        io.setX(io.getWordRegister(Register.Y));
                        opLongDesc = "Y->X, X'=" + io.getWordRegister(Register.X);
                        break;

                    /* Y -> U */
                    case 0x23:
                        io.setU(io.getWordRegister(Register.Y));
                        opLongDesc = "Y->U, U'=" + io.getWordRegister(Register.U);
                        break;

                    /* Y -> S */
                    case 0x24:
                        io.setS(io.getWordRegister(Register.Y));
                        opLongDesc = "Y->S, S'=" + io.getWordRegister(Register.S);
                        break;

                    /* Y -> PC */
                    case 0x25:
                        io.setPC(io.getWordRegister(Register.Y));
                        opLongDesc = "Y->PC, PC'=" + io.getWordRegister(Register.PC);
                        break;

                    /* U -> A:B */
                    case 0x30:
                        io.setD(io.getWordRegister(Register.U));
                        opLongDesc = "U->D, D'=" + io.getWordRegister(Register.D);
                        break;

                    /* U -> X */
                    case 0x31:
                        io.setX(io.getWordRegister(Register.U));
                        opLongDesc = "U->X, X'=" + io.getWordRegister(Register.X);
                        break;

                    /* U -> Y */
                    case 0x32:
                        io.setY(io.getWordRegister(Register.U));
                        opLongDesc = "U->Y, Y'=" + io.getWordRegister(Register.Y);
                        break;

                    /* U -> S */
                    case 0x34:
                        io.setS(io.getWordRegister(Register.U));
                        opLongDesc = "U->S, S'=" + io.getWordRegister(Register.S);
                        break;

                    /* U -> PC */
                    case 0x35:
                        io.setPC(io.getWordRegister(Register.U));
                        opLongDesc = "U->PC, PC'=" + io.getWordRegister(Register.PC);
                        break;

                    /* S -> A:B */
                    case 0x40:
                        io.setD(io.getWordRegister(Register.S));
                        opLongDesc = "S->D, D'=" + io.getWordRegister(Register.D);
                        break;

                    /* S -> X */
                    case 0x41:
                        io.setX(io.getWordRegister(Register.S));
                        opLongDesc = "S->X, X'=" + io.getWordRegister(Register.X);
                        break;

                    /* S -> Y */
                    case 0x42:
                        io.setY(io.getWordRegister(Register.S));
                        opLongDesc = "S->Y, Y'=" + io.getWordRegister(Register.Y);
                        break;

                    /* S -> U */
                    case 0x43:
                        io.setU(io.getWordRegister(Register.S));
                        opLongDesc = "S->U, U'=" + io.getWordRegister(Register.U);
                        break;

                    /* S -> PC */
                    case 0x45:
                        io.setPC(io.getWordRegister(Register.S));
                        opLongDesc = "S->PC, PC'=" + io.getWordRegister(Register.PC);
                        break;

                    /* PC -> A:B */
                    case 0x50:
                        io.setD(io.getWordRegister(Register.PC));
                        opLongDesc = "PC->D, D'=" + io.getWordRegister(Register.D);
                        break;

                    /* PC -> X */
                    case 0x51:
                        io.setX(io.getWordRegister(Register.PC));
                        opLongDesc = "PC->X, X'=" + io.getWordRegister(Register.X);
                        break;

                    /* PC -> Y */
                    case 0x52:
                        io.setY(io.getWordRegister(Register.PC));
                        opLongDesc = "PC->Y, Y'=" + io.getWordRegister(Register.Y);
                        break;

                    /* PC -> U */
                    case 0x53:
                        io.setU(io.getWordRegister(Register.PC));
                        opLongDesc = "PC->U, U'=" + io.getWordRegister(Register.U);
                        break;

                    /* PC -> S */
                    case 0x54:
                        io.setS(io.getWordRegister(Register.PC));
                        opLongDesc = "PC->S, S'=" + io.getWordRegister(Register.S);
                        break;

                    /* A -> B */
                    case 0x89:
                        io.setB(io.getByteRegister(Register.A));
                        opLongDesc = "A->B, B'=" + io.getByteRegister(Register.B);
                        break;

                    /* A -> CC */
                    case 0x8A:
                        io.setCC(io.getByteRegister(Register.A));
                        opLongDesc = "A->CC, CC'=" + io.getByteRegister(Register.CC);
                        break;

                    /* A -> DP */
                    case 0x8B:
                        io.setDP(io.getByteRegister(Register.A));
                        opLongDesc = "A->DP, DP'=" + io.getByteRegister(Register.DP);
                        break;

                    /* B -> A */
                    case 0x98:
                        io.setA(io.getByteRegister(Register.B));
                        opLongDesc = "B->A, A'=" + io.getByteRegister(Register.A);
                        break;

                    /* B -> CC */
                    case 0x9A:
                        io.setCC(io.getByteRegister(Register.B));
                        opLongDesc = "B->CC, CC'=" + io.getByteRegister(Register.CC);
                        break;

                    /* B -> DP */
                    case 0x9B:
                        io.setDP(io.getByteRegister(Register.B));
                        opLongDesc = "B->DP, DP'=" + io.getByteRegister(Register.DP);
                        break;

                    /* CC -> A */
                    case 0xA8:
                        io.setA(io.getByteRegister(Register.CC));
                        opLongDesc = "CC->A, A'=" + io.getByteRegister(Register.A);
                        break;

                    /* CC -> B */
                    case 0xA9:
                        io.setB(io.getByteRegister(Register.CC));
                        opLongDesc = "CC->B, B'=" + io.getByteRegister(Register.B);
                        break;

                    /* CC -> DP */
                    case 0xAB:
                        io.setDP(io.getByteRegister(Register.CC));
                        opLongDesc = "CC->DP, DP'=" + io.getByteRegister(Register.DP);
                        break;

                    /* DP -> A */
                    case 0xB8:
                        io.setA(io.getByteRegister(Register.DP));
                        opLongDesc = "DP->A, A'=" + io.getByteRegister(Register.A);
                        break;

                    /* DP -> B */
                    case 0xB9:
                        io.setB(io.getByteRegister(Register.DP));
                        opLongDesc = "DP->B, B'=" + io.getByteRegister(Register.B);
                        break;

                    /* DP -> CC */
                    case 0xBA:
                        io.setCC(io.getByteRegister(Register.DP));
                        opLongDesc = "DP->CC, CC'=" + io.getByteRegister(Register.CC);
                        break;

                    /* Self to self - ignored */
                    case 0x00:
                    case 0x11:
                    case 0x22:
                    case 0x33:
                    case 0x44:
                    case 0x55:
                    case 0x88:
                    case 0x99:
                    case 0xAA:
                    case 0xBB:
                        break;

                    default:
                        throw new RuntimeException("Illegal transfer " + extendedOp);
                }
            }
            break;

            /* BRA - Branch Always */
            case 0x20:
                memoryResult = io.getImmediateByte();
                branchShort(memoryResult.get().getHigh());
                operationTicks = 3;
                setShortDesc("BRA, IMM [%04X]", memoryResult);
                break;

            /* BRN - Branch Never */
            case 0x21:
                memoryResult = io.getImmediateByte();
                operationTicks = 3;
                setShortDesc("BRN, IMM [%04X]", memoryResult);
                break;

            /* BHI - Branch on Higher */
            case 0x22:
                memoryResult = io.getImmediateByte();
                if (!io.ccCarrySet() && !io.ccZeroSet()) {
                    branchShort(memoryResult.get().getHigh());
                    opLongDesc = "C=" + io.ccCarrySet() + ", Z=" + io.ccZeroSet() + ", Branching";
                } else {
                    opLongDesc = "C=" + io.ccCarrySet() + ", Z=" + io.ccZeroSet() + ", Not Branching";
                }
                operationTicks = 3;
                setShortDesc("BHI, REL [%04X]", memoryResult);
                break;

            /* BLS - Branch on Lower or Same */
            case 0x23:
                memoryResult = io.getImmediateByte();
                if (io.ccCarrySet() || io.ccZeroSet()) {
                    opLongDesc = "C=" + io.ccCarrySet() + ", Z=" + io.ccZeroSet() + ", Branching";
                    branchShort(memoryResult.get().getHigh());
                } else {
                    opLongDesc = "C=" + io.ccCarrySet() + ", Z=" + io.ccZeroSet() + ", Not Branching";
                }
                operationTicks = 5;
                setShortDesc("BLS, REL [%04X]", memoryResult);
                break;

            /* BCC - Branch on Carry Clear */
            case 0x24:
                memoryResult = io.getImmediateByte();
                if (!io.ccCarrySet()) {
                    branchShort(memoryResult.get().getHigh());
                    opLongDesc = "C=false, branching";
                } else {
                    opLongDesc = "C=true, not branching";
                }
                operationTicks = 3;
                setShortDesc("BCC, REL [%04X]", memoryResult);
                break;

            /* BCS - Branch on Carry Set */
            case 0x25:
                memoryResult = io.getImmediateByte();
                if (io.ccCarrySet()) {
                    branchShort(memoryResult.get().getHigh());
                    opLongDesc = "C=" + io.ccCarrySet() + ", Branching";
                } else {
                    opLongDesc = "C=" + io.ccCarrySet() + ", Not Branching";

                }
                operationTicks = 3;
                setShortDesc("BCS, REL [%04X]", memoryResult);
                break;

            /* BNE - Branch on Not Equal */
            case 0x26:
                memoryResult = io.getImmediateByte();
                if (!io.ccZeroSet()) {
                    branchShort(memoryResult.get().getHigh());
                    opLongDesc = "Z=" + io.ccZeroSet() + ", Branching";
                } else {
                    opLongDesc = "Z=" + io.ccZeroSet() + ", Not Branching";
                }
                operationTicks = 3;
                setShortDesc("BNE, REL [%04X]", memoryResult);
                break;

            /* BEQ - Branch on Equal */
            case 0x27:
                memoryResult = io.getImmediateByte();
                if (io.ccZeroSet()) {
                    branchShort(memoryResult.get().getHigh());
                    opLongDesc = "Z=" + io.ccZeroSet() + ", Branching";
                } else {
                    opLongDesc = "Z=" + io.ccZeroSet() + ", Not Branching";
                }
                operationTicks = 3;
                setShortDesc("BEQ, REL [%04X]", memoryResult);
                break;

            /* BVC - Branch on Overflow Clear */
            case 0x28:
                memoryResult = io.getImmediateByte();
                if (!io.ccOverflowSet()) {
                    branchShort(memoryResult.get().getHigh());
                    opLongDesc = "V=" + io.ccOverflowSet() + ", Branching";
                } else {
                    opLongDesc = "V=" + io.ccOverflowSet() + ", Not Branching";
                }
                operationTicks = 3;
                setShortDesc("BVC, REL [%04X]", memoryResult);
                break;

            /* BVS - Branch on Overflow Set */
            case 0x29:
                memoryResult = io.getImmediateByte();
                if (io.ccOverflowSet()) {
                    branchShort(memoryResult.get().getHigh());
                    opLongDesc = "V=" + io.ccOverflowSet() + ", Branching";
                } else {
                    opLongDesc = "V=" + io.ccOverflowSet() + ", Not Branching";
                }
                operationTicks = 3;
                setShortDesc("BVS, REL [%04X]", memoryResult);
                break;

            /* BPL - Branch on Plus */
            case 0x2A:
                memoryResult = io.getImmediateByte();
                if (!io.ccNegativeSet()) {
                    branchShort(memoryResult.get().getHigh());
                    opLongDesc = "N=" + io.ccNegativeSet() + ", Branching";
                } else {
                    opLongDesc = "N=" + io.ccNegativeSet() + ", Not Branching";
                }
                operationTicks = 3;
                setShortDesc("BPL, REL [%04X]", memoryResult);
                break;

            /* BMI - Branch on Minus */
            case 0x2B:
                memoryResult = io.getImmediateByte();
                if (io.ccNegativeSet()) {
                    branchShort(memoryResult.get().getHigh());
                    opLongDesc = "N=" + io.ccNegativeSet() + ", Branching";
                } else {
                    opLongDesc = "N=" + io.ccNegativeSet() + ", Not Branching";
                }
                operationTicks = 3;
                setShortDesc("BMI, REL [%04X]", memoryResult);
                break;

            /* BGE - Branch on Greater Than or Equal to Zero */
            case 0x2C:
                memoryResult = io.getImmediateByte();
                if (io.ccNegativeSet() == io.ccOverflowSet()) {
                    branchShort(memoryResult.get().getHigh());
                    opLongDesc = "N=" + io.ccNegativeSet() + ", V=" + io.ccOverflowSet() + ", Branching";
                } else {
                    opLongDesc = "N=" + io.ccNegativeSet() + ", V=" + io.ccOverflowSet() + ", Not Branching";
                }
                operationTicks = 3;
                setShortDesc("BGE, REL [%04X]", memoryResult);
                break;

            /* BLT - Branch on Less Than or Equal to Zero */
            case 0x2D:
                memoryResult = io.getImmediateByte();
                if (io.ccNegativeSet() != io.ccOverflowSet()) {
                    branchShort(memoryResult.get().getHigh());
                    opLongDesc = "N=" + io.ccNegativeSet() + ", V=" + io.ccOverflowSet() + ", Branching";
                } else {
                    opLongDesc = "N=" + io.ccNegativeSet() + ", V=" + io.ccOverflowSet() + ", Not Branching";
                }
                operationTicks = 5;
                setShortDesc("BLT, REL [%04X]", memoryResult);
                break;

            /* BGT - Branch on Greater Than Zero */
            case 0x2E:
                memoryResult = io.getImmediateByte();
                if (!io.ccZeroSet() && io.ccNegativeSet() == io.ccOverflowSet()) {
                    branchShort(memoryResult.get().getHigh());
                    opLongDesc = "N=" + io.ccNegativeSet() + ", V=" + io.ccOverflowSet() + ", Z=" + io.ccZeroSet() + ", Branching";
                } else {
                    opLongDesc = "N=" + io.ccNegativeSet() + ", V=" + io.ccOverflowSet() + ", Z=" + io.ccZeroSet() + ", Not Branching";
                }
                operationTicks = 3;
                setShortDesc("BGT, REL [%04X]", memoryResult);
                break;

            /* BLE - Branch on Less Than Zero */
            case 0x2F:
                memoryResult = io.getImmediateByte();
                if (io.ccZeroSet() || (io.ccNegativeSet() != io.ccOverflowSet())) {
                    branchShort(memoryResult.get().getHigh());
                    opLongDesc = "N=" + io.ccNegativeSet() + ", V=" + io.ccOverflowSet() + ", Z=" + io.ccZeroSet() + ", Branching";
                } else {
                    opLongDesc = "N=" + io.ccNegativeSet() + ", V=" + io.ccOverflowSet() + ", Z=" + io.ccZeroSet() + ", Not Branching";
                }
                operationTicks = 3;
                setShortDesc("BLE, REL [%04X]", memoryResult);
                break;

            /* LEAX - Load Effective Address into X register */
            case 0x30:
                memoryResult = io.getIndexed();
                loadEffectiveAddress(Register.X, memoryResult.get());
                operationTicks = 2 + memoryResult.getBytesConsumed();
                setShortDesc("LEAX, IND [%04X]", memoryResult);
                break;

            /* LEAY - Load Effective Address into Y register */
            case 0x31:
                memoryResult = io.getIndexed();
                loadEffectiveAddress(Register.Y, memoryResult.get());
                operationTicks = 2 + memoryResult.getBytesConsumed();
                setShortDesc("LEAY, IND [%04X]", memoryResult);
                break;

            /* LEAS - Load Effective Address into S register */
            case 0x32:
                memoryResult = io.getIndexed();
                loadEffectiveAddress(Register.S, memoryResult.get());
                operationTicks = 2 + memoryResult.getBytesConsumed();
                setShortDesc("LEAS, IMM [%04X]", memoryResult);
                break;

            /* LEAU - Load Effective Address into U register */
            case 0x33:
                memoryResult = io.getIndexed();
                loadEffectiveAddress(Register.U, memoryResult.get());
                operationTicks = 2 + memoryResult.getBytesConsumed();
                setShortDesc("LEAU, IND [%04X]", memoryResult);
                break;

            /* PSHS - Push Registers onto S Stack */
            case 0x34:
                memoryResult = io.getImmediateByte();
                bytes = pushStack(Register.S, memoryResult.get().getHigh());
                operationTicks = 5 + bytes;
                setShortDesc("PSHS, IMM [%04X]", memoryResult);
                break;

            /* PULS - Pull Registers from S Stack */
            case 0x35:
                memoryResult = io.getImmediateByte();
                bytes = popStack(Register.S, memoryResult.get().getHigh());
                operationTicks = 5 + bytes;
                setShortDesc("PULS, IMM [%04X]", memoryResult);
                break;

            /* PSHU - Push Registers onto U Stack */
            case 0x36:
                memoryResult = io.getImmediateByte();
                bytes = pushStack(Register.U, memoryResult.get().getHigh());
                operationTicks = 5 + bytes;
                setShortDesc("PSHU, IMM [%04X]", memoryResult);
                break;

            /* PULU - Pull Registers from U Stack */
            case 0x37:
                memoryResult = io.getImmediateByte();
                bytes = popStack(Register.U, memoryResult.get().getHigh());
                operationTicks = 5 + bytes;
                setShortDesc("PULU, IMM [%04X]", memoryResult);
                break;

            /* RTS - Return from Subroutine */
            case 0x39:
                io.setPC(
                        new UnsignedWord(
                                io.popStack(Register.S),
                                io.popStack(Register.S)
                        )
                );
                opLongDesc = "PC'=" + io.getWordRegister(Register.PC);
                operationTicks = 5;
                setShortDesc("RTS, IMM", null);
                break;

            /* ABX - Add Accumulator B into X */
            case 0x3A:
                opLongDesc = "X=" + io.getWordRegister(Register.X);
                tempWord = new UnsignedWord(
                        new UnsignedByte(),
                        io.getByteRegister(Register.B)
                );
                io.setX(
                        io.binaryAdd(io.getWordRegister(Register.X), tempWord, false, false, false)
                );
                operationTicks = 3;
                setShortDesc("ABX, IMM", null);
                opLongDesc += ", B=" + io.getByteRegister(Register.B) + ", X'=" + io.getWordRegister(Register.X);
                break;

            /* RTI - Return from Interrupt */
            case 0x3B:
                io.setCC(io.popStack(Register.S));
                if (io.ccEverythingSet()) {
                    operationTicks = 9;
                    io.setA(io.popStack(Register.S));
                    io.setB(io.popStack(Register.S));
                    io.setDP(io.popStack(Register.S));
                    io.setX(
                            new UnsignedWord(
                                    io.popStack(Register.S),
                                    io.popStack(Register.S)
                            )
                    );
                    io.setY(
                            new UnsignedWord(
                                    io.popStack(Register.S),
                                    io.popStack(Register.S)
                            )
                    );
                    io.setU(
                            new UnsignedWord(
                                    io.popStack(Register.S),
                                    io.popStack(Register.S)
                            )
                    );
                }
                io.setPC(
                        new UnsignedWord(
                                io.popStack(Register.S),
                                io.popStack(Register.S)
                        )
                );
                operationTicks += 6;
                setShortDesc("RTI, IMM", null);
                break;

            /* CWAI - Call and Wait for Interrupt */
            case 0x3C:
                memoryResult = io.getImmediateByte();
                UnsignedByte cc = io.getByteRegister(Register.CC);
                cc.and(memoryResult.get().getHigh().getShort());
                cc.or(IOController.CC_E);
                io.pushStack(Register.S, io.getWordRegister(Register.PC));
                io.pushStack(Register.S, io.getWordRegister(Register.U));
                io.pushStack(Register.S, io.getWordRegister(Register.Y));
                io.pushStack(Register.S, io.getWordRegister(Register.X));
                io.pushStack(Register.S, io.getByteRegister(Register.DP));
                io.pushStack(Register.S, io.getByteRegister(Register.B));
                io.pushStack(Register.S, io.getByteRegister(Register.A));
                io.pushStack(Register.S, io.getByteRegister(Register.CC));
                operationTicks = 20;
                setShortDesc("CWAI, IMM [%04X]", memoryResult);
                break;

            /* MUL - Multiply Unsigned */
            case 0x3D:
                a = io.getByteRegister(Register.A);
                b = io.getByteRegister(Register.B);
                opLongDesc += "A=" + a + ", B=" + b + ", ";
                UnsignedWord tempResult = new UnsignedWord(a.getShort() * b.getShort());
                io.setD(tempResult);
                opLongDesc += "D'=" + tempResult;
                cc = io.getCC();
                cc.and(~(IOController.CC_Z | IOController.CC_C));
                cc.or(tempResult.isZero() ? IOController.CC_Z : 0);
                cc.or(tempResult.isMasked(0x80) ? IOController.CC_C : 0);
                operationTicks = 11;
                setShortDesc("MUL, IMM", null);
                break;

            /* SWI - Software Interrupt */
            case 0x3F:
                softwareInterrupt(SWI);
                operationTicks = 19;
                setShortDesc("SWI", null);
                break;

            /* NEGA - Negate A */
            case 0x40:
                io.setA(negate(io.getByteRegister(Register.A)));
                operationTicks = 2;
                setShortDesc("NEGA, IMM", null);
                break;

            /* COMA - Compliment A */
            case 0x43:
                io.setA(compliment(io.getByteRegister(Register.A)));
                operationTicks = 2;
                setShortDesc("COMA, IMM", null);
                break;

            /* LSRA - Logical Shift Right A */
            case 0x44:
                io.setA(logicalShiftRight(io.getByteRegister(Register.A)));
                operationTicks = 2;
                setShortDesc("LSRA, IMM", null);
                break;

            /* RORA - Rotate Right A */
            case 0x46:
                io.setA(rotateRight(io.getByteRegister(Register.A)));
                operationTicks = 2;
                setShortDesc("RORA, IMM", null);
                break;

            /* ASRA - Arithmetic Shift Right A */
            case 0x47:
                io.setA(arithmeticShiftRight(io.getByteRegister(Register.A)));
                operationTicks = 2;
                setShortDesc("ASRA, IMM", null);
                break;

            /* ASLA - Arithmetic Shift Left A */
            case 0x48:
                io.setA(arithmeticShiftLeft(io.getByteRegister(Register.A)));
                operationTicks = 2;
                setShortDesc("ASLA, IMM", null);
                break;

            /* ROLA - Rotate Left A */
            case 0x49:
                io.setA(rotateLeft(io.getByteRegister(Register.A)));
                operationTicks = 2;
                setShortDesc("ROLA, IMM", null);
                break;

            /* DECA - Decrement A */
            case 0x4A:
                io.setA(decrement(io.getByteRegister(Register.A)));
                operationTicks = 2;
                setShortDesc("DECA, IMM", null);
                break;

            /* INCA - Increment A */
            case 0x4C:
                io.setA(increment(io.getByteRegister(Register.A)));
                operationTicks = 2;
                setShortDesc("INCA, IMM", null);
                break;

            /* TSTA - Test A */
            case 0x4D:
                io.setA(test(io.getByteRegister(Register.A)));
                operationTicks = 2;
                setShortDesc("TSTA, IMM", null);
                break;

            /* CLRA - Clear A */
            case 0x4F:
                io.setA(clear(io.getByteRegister(Register.A)));
                operationTicks = 2;
                setShortDesc("CLRA, IMM", null);
                break;

            /* NEGB - Negate B */
            case 0x50:
                io.setB(negate(io.getByteRegister(Register.B)));
                operationTicks = 2;
                setShortDesc("NEGB, IMM", null);
                break;

            /* COMB - Compliment B */
            case 0x53:
                io.setB(compliment(io.getByteRegister(Register.B)));
                operationTicks = 2;
                setShortDesc("COMB, IMM", null);
                break;

            /* LSRB - Logical Shift Right B */
            case 0x54:
                io.setB(logicalShiftRight(io.getByteRegister(Register.B)));
                operationTicks = 2;
                setShortDesc("LSRB, IMM", null);
                break;

            /* RORB - Rotate Right B */
            case 0x56:
                io.setB(rotateRight(io.getByteRegister(Register.B)));
                operationTicks = 2;
                setShortDesc("RORB, IMM", null);
                break;

            /* ASRB - Arithmetic Shift Right B */
            case 0x57:
                io.setB(arithmeticShiftRight(io.getByteRegister(Register.B)));
                operationTicks = 2;
                setShortDesc("ASRB, IMM", null);
                break;

            /* ASLB - Arithmetic Shift Left B */
            case 0x58:
                io.setB(arithmeticShiftLeft(io.getByteRegister(Register.B)));
                operationTicks = 2;
                setShortDesc("ASLB, IMM", null);
                break;

            /* ROLB - Rotate Left B */
            case 0x59:
                io.setB(rotateLeft(io.getByteRegister(Register.B)));
                operationTicks = 2;
                setShortDesc("ROLB, IMM", null);
                break;

            /* DECB - Decrement B */
            case 0x5A:
                io.setB(decrement(io.getByteRegister(Register.B)));
                operationTicks = 2;
                setShortDesc("DECB, IMM", null);
                break;

            /* INCB - Increment B */
            case 0x5C:
                io.setB(increment(io.getByteRegister(Register.B)));
                operationTicks = 2;
                setShortDesc("INCB, IMM", null);
                break;

            /* TSTB - Test B */
            case 0x5D:
                io.setB(test(io.getByteRegister(Register.B)));
                operationTicks = 2;
                setShortDesc("TSTB, IMM", null);
                break;

            /* CLRB - Clear B */
            case 0x5F:
                io.setB(clear(io.getByteRegister(Register.B)));
                operationTicks = 2;
                setShortDesc("CLRB, IMM", null);
                break;

            /* NEG - Negate M - Indexed */
            case 0x60:
                memoryResult = io.getIndexed();
                operationTicks = 4 + memoryResult.getBytesConsumed();
                executeByteFunctionM(this::negate, memoryResult);
                setShortDesc("NEGM, IND [%04X]", memoryResult);
                break;

            /* COM - Complement M - Indexed */
            case 0x63:
                memoryResult = io.getIndexed();
                operationTicks = 4 + memoryResult.getBytesConsumed();
                executeByteFunctionM(this::compliment, memoryResult);
                setShortDesc("COMM, IND [%04X]", memoryResult);
                break;

            /* LSR - Logical Shift Right - Indexed */
            case 0x64:
                memoryResult = io.getIndexed();
                operationTicks = 4 + memoryResult.getBytesConsumed();
                executeByteFunctionM(this::logicalShiftRight, memoryResult);
                setShortDesc("LSRM, IND [%04X]", memoryResult);
                break;

            /* ROR - Rotate Right - Indexed */
            case 0x66:
                memoryResult = io.getIndexed();
                operationTicks = 4 + memoryResult.getBytesConsumed();
                executeByteFunctionM(this::rotateRight, memoryResult);
                setShortDesc("RORM, IND [%04X]", memoryResult);
                break;

            /* ASR - Arithmetic Shift Right - Indexed */
            case 0x67:
                memoryResult = io.getIndexed();
                operationTicks = 4 + memoryResult.getBytesConsumed();
                executeByteFunctionM(this::arithmeticShiftRight, memoryResult);
                setShortDesc("ASRM, IND [%04X]", memoryResult);
                break;

            /* ASL - Arithmetic Shift Left - Indexed */
            case 0x68:
                memoryResult = io.getIndexed();
                operationTicks = 4 + memoryResult.getBytesConsumed();
                executeByteFunctionM(this::arithmeticShiftLeft, memoryResult);
                setShortDesc("ASLM, IND [%04X]", memoryResult);
                break;

            /* ROL - Rotate Left - Indexed */
            case 0x69:
                memoryResult = io.getIndexed();
                operationTicks = 4 + memoryResult.getBytesConsumed();
                executeByteFunctionM(this::rotateLeft, memoryResult);
                setShortDesc("ROLM, IND [%04X]", memoryResult);
                break;

            /* DEC - Decrement - Indexed */
            case 0x6A:
                memoryResult = io.getIndexed();
                operationTicks = 4 + memoryResult.getBytesConsumed();
                executeByteFunctionM(this::decrement, memoryResult);
                setShortDesc("DECM, IND [%04X]", memoryResult);
                break;

            /* INC - Increment - Indexed */
            case 0x6C:
                memoryResult = io.getIndexed();
                operationTicks = 4 + memoryResult.getBytesConsumed();
                executeByteFunctionM(this::increment, memoryResult);
                setShortDesc("INCM, IND [%04X]", memoryResult);
                break;

            /* TST - Test - Indexed */
            case 0x6D:
                memoryResult = io.getIndexed();
                operationTicks = 4 + memoryResult.getBytesConsumed();
                executeByteFunctionM(this::test, memoryResult);
                setShortDesc("TSTM, IND [%04X]", memoryResult);
                break;

            /* JMP - Jump - Indexed */
            case 0x6E:
                memoryResult = io.getIndexed();
                operationTicks = 1 + memoryResult.getBytesConsumed();
                jump(memoryResult.get());
                setShortDesc("JMP, IND [%04X]", memoryResult);
                break;

            /* CLR - Clear - Indexed */
            case 0x6F:
                memoryResult = io.getIndexed();
                operationTicks = 4 + memoryResult.getBytesConsumed();
                executeByteFunctionM(this::clear, memoryResult);
                setShortDesc("CLRM, IND [%04X]", memoryResult);
                break;

            /* NEG - Negate M - Extended */
            case 0x70:
                memoryResult = io.getExtended();
                operationTicks = 7;
                executeByteFunctionM(this::negate, memoryResult);
                setShortDesc("NEGM, EXT [%04X]", memoryResult);
                break;

            /* COM - Complement M - Extended */
            case 0x73:
                memoryResult = io.getExtended();
                operationTicks = 7;
                executeByteFunctionM(this::compliment, memoryResult);
                setShortDesc("COMM, EXT [%04X]", memoryResult);
                break;

            /* LSR - Logical Shift Right - Extended */
            case 0x74:
                memoryResult = io.getExtended();
                operationTicks = 7;
                executeByteFunctionM(this::logicalShiftRight, memoryResult);
                setShortDesc("LSRM, EXT [%04X]", memoryResult);
                break;

            /* ROR - Rotate Right - Extended */
            case 0x76:
                memoryResult = io.getExtended();
                operationTicks = 7;
                executeByteFunctionM(this::rotateRight, memoryResult);
                setShortDesc("RORM, EXT [%04X]", memoryResult);
                break;

            /* ASR - Arithmetic Shift Right - Extended */
            case 0x77:
                memoryResult = io.getExtended();
                operationTicks = 7;
                executeByteFunctionM(this::arithmeticShiftRight, memoryResult);
                setShortDesc("ASRM, EXT [%04X]", memoryResult);
                break;

            /* ASL - Arithmetic Shift Left - Extended */
            case 0x78:
                memoryResult = io.getExtended();
                operationTicks = 7;
                executeByteFunctionM(this::arithmeticShiftLeft, memoryResult);
                setShortDesc("ASLM, EXT [%04X]", memoryResult);
                break;

            /* ROL - Rotate Left - Extended */
            case 0x79:
                memoryResult = io.getExtended();
                operationTicks = 7;
                executeByteFunctionM(this::rotateLeft, memoryResult);
                setShortDesc("ROLM, EXT [%04X]", memoryResult);
                break;

            /* DEC - Decrement - Extended */
            case 0x7A:
                memoryResult = io.getExtended();
                operationTicks = 7;
                executeByteFunctionM(this::decrement, memoryResult);
                setShortDesc("DECM, EXT [%04X]", memoryResult);
                break;

            /* INC - Increment - Extended */
            case 0x7C:
                memoryResult = io.getExtended();
                operationTicks = 7;
                executeByteFunctionM(this::increment, memoryResult);
                setShortDesc("INCM, EXT [%04X]", memoryResult);
                break;

            /* TST - Test - Extended */
            case 0x7D:
                memoryResult = io.getExtended();
                operationTicks = 7;
                executeByteFunctionM(this::test, memoryResult);
                setShortDesc("TSTM, EXT [%04X]", memoryResult);
                break;

            /* JMP - Jump - Extended */
            case 0x7E:
                memoryResult = io.getExtended();
                operationTicks = 4;
                jump(memoryResult.get());
                setShortDesc("JMP, EXT [%04X]", memoryResult);
                break;

            /* CLR - Clear - Extended */
            case 0x7F:
                memoryResult = io.getExtended();
                operationTicks = 7;
                executeByteFunctionM(this::clear, memoryResult);
                setShortDesc("CLRM, EXT [%04X]", memoryResult);
                break;

            /* SUBA - Subtract M from A - Immediate */
            case 0x80:
                memoryResult = io.getImmediateByte();
                subtractM(Register.A, memoryResult.get().getHigh());
                operationTicks = 2;
                setShortDesc("SUBA, IMM [%04X]", memoryResult);
                break;

            /* CMPA - Compare A - Immediate */
            case 0x81:
                memoryResult = io.getImmediateByte();
                compareByte(io.getByteRegister(Register.A), memoryResult.get().getHigh());
                operationTicks = 2;
                setShortDesc("CMPA, IMM [%04X]", memoryResult);
                break;

            /* SBCA - Subtract M and C from A - Immediate */
            case 0x82:
                memoryResult = io.getImmediateByte();
                subtractMC(Register.A, memoryResult.get().getHigh());
                operationTicks = 2;
                setShortDesc("SBCA, IMM [%04X]", memoryResult);
                break;

            /* SUBD - Subtract M from D - Immediate */
            case 0x83:
                memoryResult = io.getImmediateWord();
                subtractD(memoryResult.get());
                operationTicks = 4;
                setShortDesc("SUBD, IMM [%04X]", memoryResult);
                break;

            /* ANDA - Logical AND A - Immediate */
            case 0x84:
                memoryResult = io.getImmediateByte();
                logicalAnd(Register.A, memoryResult.get().getHigh());
                operationTicks = 2;
                setShortDesc("ANDA, IMM", null);
                break;

            /* BITA - Test A - Immediate */
            case 0x85:
                memoryResult = io.getImmediateByte();
                test(new UnsignedByte(io.getByteRegister(Register.A).getShort() & memoryResult.get().getHigh().getShort()));
                operationTicks = 2;
                setShortDesc("BITA, IMM [%04X]", memoryResult);
                break;

            /* LDA - Load A - Immediate */
            case 0x86:
                memoryResult = io.getImmediateByte();
                loadByteRegister(Register.A, memoryResult.get().getHigh());
                operationTicks = 4;
                setShortDesc("LDA, IMM [%04X]", memoryResult);
                break;

            /* EORA - Exclusive OR A - Immediate */
            case 0x88:
                memoryResult = io.getImmediateByte();
                exclusiveOr(Register.A, memoryResult.get().getHigh());
                operationTicks = 2;
                setShortDesc("EORA, IMM [%04X]", memoryResult);
                break;

            /* ADCA - Add with Carry A - Immediate */
            case 0x89:
                memoryResult = io.getImmediateByte();
                addWithCarry(Register.A, memoryResult.get().getHigh());
                operationTicks = 2;
                setShortDesc("ADCA, IMM [%04X]", memoryResult);
                break;

            /* ORA - Logical OR A - Immediate */
            case 0x8A:
                memoryResult = io.getImmediateByte();
                logicalOr(Register.A, memoryResult.get().getHigh());
                operationTicks = 2;
                setShortDesc("ORA, IMM [%04X]", memoryResult);
                break;

            /* ADDA - Add A - Immediate */
            case 0x8B:
                memoryResult = io.getImmediateByte();
                addByteRegister(Register.A, memoryResult.get().getHigh());
                operationTicks = 2;
                setShortDesc("ADDA, IMM [%04X]", memoryResult);
                break;

            /* CMPX - Compare X - Immediate */
            case 0x8C:
                memoryResult = io.getImmediateWord();
                compareWord(io.getWordRegister(Register.X), memoryResult.get());
                operationTicks = 4;
                setShortDesc("CMPX, IMM [%04X]", memoryResult);
                break;

            /* BSR - Branch to Subroutine - Immediate */
            case 0x8D:
                memoryResult = io.getImmediateByte();
                io.pushStack(Register.S, io.getWordRegister(Register.PC));
                branchShort(memoryResult.get().getHigh());
                operationTicks = 7;
                setShortDesc("BSR, IMM [%04X]", memoryResult);
                break;

            /* LDX - Load X - Immediate */
            case 0x8E:
                memoryResult = io.getImmediateWord();
                loadRegister(Register.X, memoryResult.get());
                operationTicks = 3;
                setShortDesc("LDX, IMM [%04X]", memoryResult);
                break;

            /* SUBA - Subtract M from A - Direct */
            case 0x90:
                memoryResult = io.getDirect();
                subtractM(Register.A, io.readByte(memoryResult.get()));
                operationTicks = 4;
                setShortDesc("SUBA, DIR [%04X]", memoryResult);
                break;

            /* CMPA - Compare A - Direct */
            case 0x91:
                memoryResult = io.getDirect();
                compareByte(io.getByteRegister(Register.A), io.readByte(memoryResult.get()));
                operationTicks = 4;
                setShortDesc("CMPA, DIR [%04X]", memoryResult);
                break;

            /* SBCA - Subtract M and C from A - Direct */
            case 0x92:
                memoryResult = io.getDirect();
                subtractMC(Register.A, io.readByte(memoryResult.get()));
                operationTicks = 4;
                setShortDesc("SBCA, DIR [%04X]", memoryResult);
                break;

            /* SUBD - Subtract M from D - Direct */
            case 0x93:
                memoryResult = io.getDirect();
                subtractD(io.readWord(memoryResult.get()));
                operationTicks = 6;
                setShortDesc("SUBD, DIR [%04X]", memoryResult);
                break;

            /* ANDA - Logical AND A - Direct */
            case 0x94:
                memoryResult = io.getDirect();
                logicalAnd(Register.A, io.readByte(memoryResult.get()));
                operationTicks = 4;
                setShortDesc("ANDA, DIR [%04X]", memoryResult);
                break;

            /* BITA - Test A - Direct */
            case 0x95:
                memoryResult = io.getDirect();
                test(new UnsignedByte(io.getByteRegister(Register.A).getShort() & io.readByte(memoryResult.get()).getShort()));
                operationTicks = 4;
                setShortDesc("BITA, DIR [%04X]", memoryResult);
                break;

            /* LDA - Load A - Direct */
            case 0x96:
                memoryResult = io.getDirect();
                loadByteRegister(Register.A, io.readByte(memoryResult.get()));
                operationTicks = 2;
                setShortDesc("LDA, DIR [%04X]", memoryResult);
                break;

            /* STA - Store A - Direct */
            case 0x97:
                memoryResult = io.getDirect();
                storeByteRegister(Register.A, memoryResult.get());
                operationTicks = 4;
                setShortDesc("STA, DIR [%04X]", memoryResult);
                break;

            /* EORA - Exclusive OR A - Direct */
            case 0x98:
                memoryResult = io.getDirect();
                exclusiveOr(Register.A, io.readByte(memoryResult.get()));
                operationTicks = 4;
                setShortDesc("EORA, DIR [%04X]", memoryResult);
                break;

            /* ADCA - Add with Carry A - Direct */
            case 0x99:
                memoryResult = io.getDirect();
                addWithCarry(Register.A, io.readByte(memoryResult.get()));
                operationTicks = 4;
                setShortDesc("ADCA, DIR [%04X]", memoryResult);
                break;

            /* ORA - Logical OR A - Direct */
            case 0x9A:
                memoryResult = io.getDirect();
                logicalOr(Register.A, io.readByte(memoryResult.get()));
                operationTicks = 4;
                setShortDesc("ORA, DIR [%04X]", memoryResult);
                break;

            /* ADDA - Add A - Direct */
            case 0x9B:
                memoryResult = io.getDirect();
                addByteRegister(Register.A, io.readByte(memoryResult.get()));
                operationTicks = 4;
                setShortDesc("ADDA, DIR [%04X]", memoryResult);
                break;

            /* CMPX - Compare X - Direct */
            case 0x9C:
                memoryResult = io.getDirect();
                compareWord(io.getWordRegister(Register.X), io.readWord(memoryResult.get()));
                operationTicks = 6;
                setShortDesc("CMPX, DIR [%04X]", memoryResult);
                break;

            /* JSR - Jump to Subroutine - Direct */
            case 0x9D:
                memoryResult = io.getDirect();
                jumpToSubroutine(memoryResult.get());
                operationTicks = 7;
                setShortDesc("JSR, DIR [%04X]", memoryResult);
                break;

            /* LDX - Load X - Direct */
            case 0x9E:
                memoryResult = io.getDirect();
                loadRegister(Register.X, io.readWord(memoryResult.get()));
                operationTicks = 5;
                setShortDesc("LDX, DIR [%04X]", memoryResult);
                break;

            /* STX - Store X - Direct */
            case 0x9F:
                memoryResult = io.getDirect();
                storeWordRegister(Register.X, memoryResult.get());
                operationTicks = 5;
                setShortDesc("STX, DIR [%04X]", memoryResult);
                break;

            /* SUBA - Subtract M from A - Indexed */
            case 0xA0:
                memoryResult = io.getIndexed();
                subtractM(Register.A, io.readByte(memoryResult.get()));
                operationTicks = 2 + memoryResult.getBytesConsumed();
                setShortDesc("SUBA, IND [%04X]", memoryResult);
                break;

            /* CMPA - Compare A - Indexed */
            case 0xA1:
                memoryResult = io.getIndexed();
                compareByte(io.getByteRegister(Register.A), io.readByte(memoryResult.get()));
                operationTicks = 4;
                setShortDesc("CMPA, IND [%04X]", memoryResult);
                break;

            /* SBCA - Subtract M and C from A - Indexed */
            case 0xA2:
                memoryResult = io.getIndexed();
                subtractMC(Register.A, io.readByte(memoryResult.get()));
                operationTicks = 2 + memoryResult.getBytesConsumed();
                setShortDesc("SBCA, IND [%04X]", memoryResult);
                break;

            /* SUBD - Subtract M from D - Indexed */
            case 0xA3:
                memoryResult = io.getIndexed();
                subtractD(io.readWord(memoryResult.get()));
                operationTicks = 4 + memoryResult.getBytesConsumed();
                setShortDesc("SUBD, IND [%04X]", memoryResult);
                break;

            /* ANDA - Logical AND A - Indexed */
            case 0xA4:
                memoryResult = io.getIndexed();
                logicalAnd(Register.A, io.readByte(memoryResult.get()));
                operationTicks = 2 + memoryResult.getBytesConsumed();
                setShortDesc("ANDA, IND [%04X]", memoryResult);
                break;

            /* BITA - Test A - Indexed */
            case 0xA5:
                memoryResult = io.getIndexed();
                test(new UnsignedByte(io.getByteRegister(Register.A).getShort() & io.readByte(memoryResult.get()).getShort()));
                operationTicks = 2 + memoryResult.getBytesConsumed();
                setShortDesc("BITA, IND [%04X]", memoryResult);
                break;

            /* LDA - Load A - Indexed */
            case 0xA6:
                memoryResult = io.getIndexed();
                loadByteRegister(Register.A, io.readByte(memoryResult.get()));
                operationTicks = 2 + memoryResult.getBytesConsumed();
                setShortDesc("LDA, IND [%04X]", memoryResult);
                break;

            /* STA - Store A - Indexed */
            case 0xA7:
                memoryResult = io.getIndexed();
                storeByteRegister(Register.A, memoryResult.get());
                operationTicks = 2 + memoryResult.getBytesConsumed();
                setShortDesc("STA, IND [%04X]", memoryResult);
                break;

            /* EORA - Exclusive OR A - Indexed */
            case 0xA8:
                memoryResult = io.getIndexed();
                exclusiveOr(Register.A, io.readByte(memoryResult.get()));
                operationTicks = 2 + memoryResult.getBytesConsumed();
                setShortDesc("EORA, IND [%04X]", memoryResult);
                break;

            /* ADCA - Add with Carry A - Indexed */
            case 0xA9:
                memoryResult = io.getIndexed();
                addWithCarry(Register.A, io.readByte(memoryResult.get()));
                operationTicks = 2 + memoryResult.getBytesConsumed();
                setShortDesc("ADCA, IND [%04X]", memoryResult);
                break;

            /* ORA - Logical OR A - Indexed */
            case 0xAA:
                memoryResult = io.getIndexed();
                logicalOr(Register.A, io.readByte(memoryResult.get()));
                operationTicks = 2 + memoryResult.getBytesConsumed();
                setShortDesc("ORA, IND [%04X]", memoryResult);
                break;

            /* ADDA - Add A - Indexed */
            case 0xAB:
                memoryResult = io.getIndexed();
                addByteRegister(Register.A, io.readByte(memoryResult.get()));
                operationTicks = 2 + memoryResult.getBytesConsumed();
                setShortDesc("ADDA, IND [%04X]", memoryResult);
                break;

            /* CMPX - Compare X - Indexed */
            case 0xAC:
                memoryResult = io.getIndexed();
                compareWord(io.getWordRegister(Register.X), io.readWord(memoryResult.get()));
                operationTicks = 4 + memoryResult.getBytesConsumed();
                setShortDesc("CMPX, IND [%04X]", memoryResult);
                break;

            /* JSR - Jump to Subroutine - Indexed */
            case 0xAD:
                memoryResult = io.getIndexed();
                jumpToSubroutine(memoryResult.get());
                operationTicks = 5 + memoryResult.getBytesConsumed();
                setShortDesc("JSR, IND [%04X]", memoryResult);
                break;

            /* LDX - Load X - Indexed */
            case 0xAE:
                memoryResult = io.getIndexed();
                loadRegister(Register.X, io.readWord(memoryResult.get()));
                operationTicks = 3 + memoryResult.getBytesConsumed();
                setShortDesc("LDX, IND [%04X]", memoryResult);
                break;

            /* STX - Store X - Indexed */
            case 0xAF:
                memoryResult = io.getIndexed();
                storeWordRegister(Register.X, memoryResult.get());
                operationTicks = 3 + memoryResult.getBytesConsumed();
                setShortDesc("STX, IND [%04X]", memoryResult);
                break;

            /* SUBA - Subtract M from A - Extended */
            case 0xB0:
                memoryResult = io.getExtended();
                subtractM(Register.A, io.readByte(memoryResult.get()));
                operationTicks = 2 + memoryResult.getBytesConsumed();
                setShortDesc("SUBA, EXT [%04X]", memoryResult);
                break;

            /* CMPA - Compare A - Extended */
            case 0xB1:
                memoryResult = io.getExtended();
                compareByte(io.getByteRegister(Register.A), io.readByte(memoryResult.get()));
                operationTicks = 5;
                setShortDesc("CMPA, EXT [%04X]", memoryResult);
                break;

            /* SBCA - Subtract M and C from A - Extended */
            case 0xB2:
                memoryResult = io.getExtended();
                subtractMC(Register.A, io.readByte(memoryResult.get()));
                operationTicks = 5;
                setShortDesc("SBCA, EXT [%04X]", memoryResult);
                break;

            /* SUBD - Subtract M from D - Extended */
            case 0xB3:
                memoryResult = io.getExtended();
                subtractD(io.readWord(memoryResult.get()));
                operationTicks = 7;
                setShortDesc("SUBD, EXT [%04X]", memoryResult);
                break;

            /* ANDA - Logical AND A - Extended */
            case 0xB4:
                memoryResult = io.getExtended();
                logicalAnd(Register.A, io.readByte(memoryResult.get()));
                operationTicks = 5;
                setShortDesc("ANDA, EXT [%04X]", memoryResult);
                break;

            /* BITA - Test A - Extended */
            case 0xB5:
                memoryResult = io.getExtended();
                test(new UnsignedByte(io.getByteRegister(Register.A).getShort() & io.readByte(memoryResult.get()).getShort()));
                operationTicks = 5;
                setShortDesc("BITA, EXT [%04X]", memoryResult);
                break;

            /* LDA - Load A - Extended */
            case 0xB6:
                memoryResult = io.getExtended();
                loadByteRegister(Register.A, io.readByte(memoryResult.get()));
                operationTicks = 5;
                setShortDesc("LDA, EXT [%04X]", memoryResult);
                break;

            /* STA - Store A - Extended */
            case 0xB7:
                memoryResult = io.getExtended();
                storeByteRegister(Register.A, memoryResult.get());
                operationTicks = 5;
                setShortDesc("STA, EXT [%04X]", memoryResult);
                break;

            /* EORA - Exclusive A - Extended */
            case 0xB8:
                memoryResult = io.getExtended();
                exclusiveOr(Register.A, io.readByte(memoryResult.get()));
                operationTicks = 5;
                setShortDesc("EORA, EXT [%04X]", memoryResult);
                break;

            /* ADCA - Add with Carry A - Extended */
            case 0xB9:
                memoryResult = io.getExtended();
                addWithCarry(Register.A, io.readByte(memoryResult.get()));
                operationTicks = 5;
                setShortDesc("ADCA, EXT [%04X]", memoryResult);
                break;

            /* ORA - Logical OR A - Extended */
            case 0xBA:
                memoryResult = io.getExtended();
                logicalOr(Register.A, io.readByte(memoryResult.get()));
                operationTicks = 5;
                setShortDesc("ORA, EXT [%04X]", memoryResult);
                break;

            /* ADDA - Add A - Extended */
            case 0xBB:
                memoryResult = io.getExtended();
                addByteRegister(Register.A, io.readByte(memoryResult.get()));
                operationTicks = 5;
                setShortDesc("ADDA, EXT [%04X]", memoryResult);
                break;

            /* CMPX - Compare X - Extended */
            case 0xBC:
                memoryResult = io.getExtended();
                compareWord(io.getWordRegister(Register.X), io.readWord(memoryResult.get()));
                operationTicks = 7;
                setShortDesc("CMPX, EXT [%04X]", memoryResult);
                break;

            /* JSR - Jump to Subroutine - Extended */
            case 0xBD:
                memoryResult = io.getExtended();
                jumpToSubroutine(memoryResult.get());
                operationTicks = 8;
                setShortDesc("JSR, EXT [%04X]", memoryResult);
                break;

            /* LDX - Load X - Extended */
            case 0xBE:
                memoryResult = io.getExtended();
                loadRegister(Register.X, io.readWord(memoryResult.get()));
                operationTicks = 6;
                setShortDesc("LDX, EXT [%04X]", memoryResult);
                break;

            /* STX - Store X - Extended */
            case 0xBF:
                memoryResult = io.getExtended();
                storeWordRegister(Register.X, memoryResult.get());
                operationTicks = 6;
                setShortDesc("STX, EXT [%04X]", memoryResult);
                break;

            /* SUBB - Subtract M from B - Immediate */
            case 0xC0:
                memoryResult = io.getImmediateByte();
                subtractM(Register.B, memoryResult.get().getHigh());
                operationTicks = 2;
                setShortDesc("SUBB, IMM [%04X]", memoryResult);
                break;

            /* CMPB - Compare B - Immediate */
            case 0xC1:
                memoryResult = io.getImmediateByte();
                compareByte(io.getByteRegister(Register.B), memoryResult.get().getHigh());
                operationTicks = 2;
                setShortDesc("CMPB, IMM [%04X]", memoryResult);
                break;

            /* SBCB - Subtract M and C from B - Immediate */
            case 0xC2:
                memoryResult = io.getImmediateByte();
                subtractMC(Register.B, memoryResult.get().getHigh());
                operationTicks = 2;
                setShortDesc("SBCB, IMM [%04X]", memoryResult);
                break;

            /* ADDD - Add D - Immediate */
            case 0xC3:
                memoryResult = io.getImmediateWord();
                addD(memoryResult.get());
                operationTicks = 4;
                setShortDesc("ADDD, IMM [%04X]", memoryResult);
                break;

            /* ANDB - Logical AND B - Immediate */
            case 0xC4:
                memoryResult = io.getImmediateByte();
                logicalAnd(Register.B, memoryResult.get().getHigh());
                operationTicks = 2;
                setShortDesc("ANDB, IMM [%04X]", memoryResult);
                break;

            /* BITB - Test B - Immediate */
            case 0xC5:
                memoryResult = io.getImmediateByte();
                test(new UnsignedByte(io.getByteRegister(Register.B).getShort() & memoryResult.get().getHigh().getShort()));
                operationTicks = 2;
                setShortDesc("BITB, IMM [%04X]", memoryResult);
                break;

            /* LDB - Load B - Immediate */
            case 0xC6:
                memoryResult = io.getImmediateByte();
                loadByteRegister(Register.B, memoryResult.get().getHigh());
                operationTicks = 4;
                setShortDesc("LDB, IMM [%04X]", memoryResult);
                break;

            /* EORB - Exclusive OR B - Immediate */
            case 0xC8:
                memoryResult = io.getImmediateByte();
                exclusiveOr(Register.B, memoryResult.get().getHigh());
                operationTicks = 2;
                setShortDesc("EORB, IMM [%04X]", memoryResult);
                break;

            /* ADCB - Add with Carry B - Immediate */
            case 0xC9:
                memoryResult = io.getImmediateByte();
                addWithCarry(Register.B, memoryResult.get().getHigh());
                operationTicks = 2;
                setShortDesc("ADCB, IMM [%04X]", memoryResult);
                break;

            /* ORB - Logical OR B - Immediate */
            case 0xCA:
                memoryResult = io.getImmediateByte();
                logicalOr(Register.B, memoryResult.get().getHigh());
                operationTicks = 2;
                setShortDesc("ORB, IMM [%04X]", memoryResult);
                break;

            /* ADDB - Add B - Immediate */
            case 0xCB:
                memoryResult = io.getImmediateByte();
                addByteRegister(Register.B, memoryResult.get().getHigh());
                operationTicks = 2;
                setShortDesc("ADDB, IMM [%04X]", memoryResult);
                break;

            /* LDD - Load D - Immediate */
            case 0xCC:
                memoryResult = io.getImmediateWord();
                loadRegister(Register.D, memoryResult.get());
                operationTicks = 3;
                setShortDesc("LDD, IMM [%04X]", memoryResult);
                break;

            /* LDU - Load U - Immediate */
            case 0xCE:
                memoryResult = io.getImmediateWord();
                loadRegister(Register.U, memoryResult.get());
                operationTicks = 3;
                setShortDesc("LDU, IMM [%04X]", memoryResult);
                break;

            /* SUBB - Subtract M from B - Direct */
            case 0xD0:
                memoryResult = io.getDirect();
                subtractM(Register.B, io.readByte(memoryResult.get()));
                operationTicks = 4;
                setShortDesc("SUBB, DIR [%04X]", memoryResult);
                break;

            /* CMPB - Compare B - Direct */
            case 0xD1:
                memoryResult = io.getDirect();
                compareByte(io.getByteRegister(Register.B), io.readByte(memoryResult.get()));
                operationTicks = 4;
                setShortDesc("CMPB, DIR [%04X]", memoryResult);
                break;

            /* SBCB - Subtract M and C from B - Direct */
            case 0xD2:
                memoryResult = io.getDirect();
                subtractMC(Register.B, io.readByte(memoryResult.get()));
                operationTicks = 4;
                setShortDesc("SBCB, DIR [%04X]", memoryResult);
                break;

            /* ADDD - Add D - Direct */
            case 0xD3:
                memoryResult = io.getDirect();
                addD(io.readWord(memoryResult.get()));
                operationTicks = 6;
                setShortDesc("ADDD, DIR [%04X]", memoryResult);
                break;

            /* ANDB - Logical AND B - Direct */
            case 0xD4:
                memoryResult = io.getDirect();
                logicalAnd(Register.B, io.readByte(memoryResult.get()));
                operationTicks = 4;
                setShortDesc("ANDB, DIR", null);
                break;

            /* BITB - Test B - Direct */
            case 0xD5:
                memoryResult = io.getDirect();
                test(new UnsignedByte(io.getByteRegister(Register.B).getShort() & io.readByte(memoryResult.get()).getShort()));
                operationTicks = 4;
                setShortDesc("BITB, DIR [%04X]", memoryResult);
                break;

            /* LDB - Load B - Direct */
            case 0xD6:
                memoryResult = io.getDirect();
                loadByteRegister(Register.B, io.readByte(memoryResult.get()));
                operationTicks = 2;
                setShortDesc("LDB, DIR [%04X]", memoryResult);
                break;

            /* STB - Store B - Direct */
            case 0xD7:
                memoryResult = io.getDirect();
                storeByteRegister(Register.B, memoryResult.get());
                operationTicks = 2;
                setShortDesc("STB, DIR [%04X]", memoryResult);
                break;

            /* EORB - Exclusive OR B - Direct */
            case 0xD8:
                memoryResult = io.getDirect();
                exclusiveOr(Register.B, io.readByte(memoryResult.get()));
                operationTicks = 4;
                setShortDesc("EORB, DIR [%04X]", memoryResult);
                break;

            /* ADCB - Add with Carry B - Direct */
            case 0xD9:
                memoryResult = io.getDirect();
                addWithCarry(Register.B, io.readByte(memoryResult.get()));
                operationTicks = 4;
                setShortDesc("ADCB, DIR [%04X]", memoryResult);
                break;

            /* ORB - Logical OR B - Direct */
            case 0xDA:
                memoryResult = io.getDirect();
                logicalOr(Register.B, io.readByte(memoryResult.get()));
                operationTicks = 4;
                setShortDesc("ORB, DIR [%04X]", memoryResult);
                break;

            /* ADDB - Add B - Direct */
            case 0xDB:
                memoryResult = io.getDirect();
                addByteRegister(Register.B, io.readByte(memoryResult.get()));
                operationTicks = 4;
                setShortDesc("ADDB, DIR [%04X]", memoryResult);
                break;

            /* LDD - Load - Direct */
            case 0xDC:
                memoryResult = io.getDirect();
                loadRegister(Register.D, io.readWord(memoryResult.get()));
                operationTicks = 5;
                setShortDesc("LDD, DIR [%04X]", memoryResult);
                break;

            /* STD - Store D - Direct */
            case 0xDD:
                memoryResult = io.getDirect();
                storeWordRegister(Register.D, memoryResult.get());
                operationTicks = 5;
                setShortDesc("STD, DIR [%04X]", memoryResult);
                break;

            /* LDU - Load U - Direct */
            case 0xDE:
                memoryResult = io.getDirect();
                loadRegister(Register.U, io.readWord(memoryResult.get()));
                operationTicks = 5;
                setShortDesc("LDU, DIR [%04X]", memoryResult);
                break;

            /* STU - Store U - Direct */
            case 0xDF:
                memoryResult = io.getDirect();
                storeWordRegister(Register.U, memoryResult.get());
                operationTicks = 5;
                setShortDesc("STU, DIR [%04X]", memoryResult);
                break;

            /* SUBB - Subtract M from B - Indexed */
            case 0xE0:
                memoryResult = io.getIndexed();
                subtractM(Register.B, io.readByte(memoryResult.get()));
                operationTicks = 2 + memoryResult.getBytesConsumed();
                setShortDesc("SUBB, IND [%04X]", memoryResult);
                break;

            /* CMPB - Compare B - Indexed */
            case 0xE1:
                memoryResult = io.getIndexed();
                compareByte(io.getByteRegister(Register.B), io.readByte(memoryResult.get()));
                operationTicks = 4;
                setShortDesc("CMPB, IND [%04X]", memoryResult);
                break;

            /* SBCB - Subtract M and C from B - Indexed */
            case 0xE2:
                memoryResult = io.getIndexed();
                subtractMC(Register.B, io.readByte(memoryResult.get()));
                operationTicks = 2 + memoryResult.getBytesConsumed();
                setShortDesc("SBCB, IND [%04X]", memoryResult);
                break;

            /* ADDD - Add D - Indexed */
            case 0xE3:
                memoryResult = io.getIndexed();
                addD(io.readWord(memoryResult.get()));
                operationTicks = 6 + memoryResult.getBytesConsumed();
                setShortDesc("ADDD, IND [%04X]", memoryResult);
                break;

            /* ANDB - Logical AND B - Indexed */
            case 0xE4:
                memoryResult = io.getIndexed();
                logicalAnd(Register.B, io.readByte(memoryResult.get()));
                operationTicks = 2 + memoryResult.getBytesConsumed();
                setShortDesc("ANDB, IND [%04X]", memoryResult);
                break;

            /* BITB - Test B - Indexed */
            case 0xE5:
                memoryResult = io.getIndexed();
                test(new UnsignedByte(io.getByteRegister(Register.B).getShort() & io.readByte(memoryResult.get()).getShort()));
                operationTicks = 2 + memoryResult.getBytesConsumed();
                setShortDesc("BITB, IND [%04X]", memoryResult);
                break;

            /* LDB - Load B - Indexed */
            case 0xE6:
                memoryResult = io.getIndexed();
                loadByteRegister(Register.B, io.readByte(memoryResult.get()));
                operationTicks = 2 + memoryResult.getBytesConsumed();
                setShortDesc("LDB, IND [%04X]", memoryResult);
                break;

            /* STB - Store B - Indexed */
            case 0xE7:
                memoryResult = io.getIndexed();
                storeByteRegister(Register.B, memoryResult.get());
                operationTicks = 2 + memoryResult.getBytesConsumed();
                setShortDesc("STB, IND [%04X]", memoryResult);
                break;

            /* EORB - Exclusive OR B - Indexed */
            case 0xE8:
                memoryResult = io.getIndexed();
                exclusiveOr(Register.B, io.readByte(memoryResult.get()));
                operationTicks = 2 + memoryResult.getBytesConsumed();
                setShortDesc("EORB, IND [%04X]", memoryResult);
                break;

            /* ADCB - Add with Carry B - Indexed */
            case 0xE9:
                memoryResult = io.getIndexed();
                addWithCarry(Register.B, io.readByte(memoryResult.get()));
                operationTicks = 2 + memoryResult.getBytesConsumed();
                setShortDesc("ADCB, IND [%04X]", memoryResult);
                break;

            /* ORB - Logical OR B - Indexed */
            case 0xEA:
                memoryResult = io.getIndexed();
                logicalOr(Register.B, io.readByte(memoryResult.get()));
                operationTicks = 2 + memoryResult.getBytesConsumed();
                setShortDesc("ORB, IND [%04X]", memoryResult);
                break;

            /* ADDB - Add B - Indexed */
            case 0xEB:
                memoryResult = io.getIndexed();
                addByteRegister(Register.B, io.readByte(memoryResult.get()));
                operationTicks = 2 + memoryResult.getBytesConsumed();
                setShortDesc("ADDB, IND [%04X]", memoryResult);
                break;

            /* LDD - Load D - Indexed */
            case 0xEC:
                memoryResult = io.getIndexed();
                loadRegister(Register.D, io.readWord(memoryResult.get()));
                operationTicks = 3 + memoryResult.getBytesConsumed();
                setShortDesc("LDD, IND [%04X]", memoryResult);
                break;

            /* STD - Store D - Indexed */
            case 0xED:
                memoryResult = io.getIndexed();
                storeWordRegister(Register.D, memoryResult.get());
                operationTicks = 3 + memoryResult.getBytesConsumed();
                setShortDesc("STD, IND [%04X]", memoryResult);
                break;

            /* LDU - Load U - Indexed */
            case 0xEE:
                memoryResult = io.getIndexed();
                loadRegister(Register.U, io.readWord(memoryResult.get()));
                operationTicks = 3 + memoryResult.getBytesConsumed();
                setShortDesc("LDU, IND [%04X]", memoryResult);
                break;

            /* STU - Store U - Indexed */
            case 0xEF:
                memoryResult = io.getIndexed();
                storeWordRegister(Register.U, memoryResult.get());
                operationTicks = 3 + memoryResult.getBytesConsumed();
                setShortDesc("STU, IND [%04X]", memoryResult);
                break;

            /* SUBB - Subtract M from B - Extended */
            case 0xF0:
                memoryResult = io.getExtended();
                subtractM(Register.B, io.readByte(memoryResult.get()));
                operationTicks = 2 + memoryResult.getBytesConsumed();
                setShortDesc("SUBB, EXT [%04X]", memoryResult);
                break;

            /* CMPB - Compare B - Extended */
            case 0xF1:
                memoryResult = io.getExtended();
                compareByte(io.getByteRegister(Register.B), io.readByte(memoryResult.get()));
                operationTicks = 5;
                setShortDesc("CMPB, EXT [%04X]", memoryResult);
                break;

            /* SBCB - Subtract M and C from B - Extended */
            case 0xF2:
                memoryResult = io.getExtended();
                subtractMC(Register.B, io.readByte(memoryResult.get()));
                operationTicks = 5;
                setShortDesc("SBCB, EXT [%04X]", memoryResult);
                break;

            /* ADDD - Add D - Extended */
            case 0xF3:
                memoryResult = io.getExtended();
                addD(io.readWord(memoryResult.get()));
                operationTicks = 7;
                setShortDesc("ADDD, EXT [%04X]", memoryResult);
                break;

            /* ANDB - Logical AND B - Extended */
            case 0xF4:
                memoryResult = io.getExtended();
                logicalAnd(Register.B, io.readByte(memoryResult.get()));
                operationTicks = 5;
                setShortDesc("ANDB, EXT [%04X]", memoryResult);
                break;

            /* BITB - Test B - Extended */
            case 0xF5:
                memoryResult = io.getExtended();
                test(new UnsignedByte(io.getByteRegister(Register.B).getShort() & io.readByte(memoryResult.get()).getShort()));
                operationTicks = 5;
                setShortDesc("BITB, EXT [%04X]", memoryResult);
                break;

            /* LDB - Load B - Extended */
            case 0xF6:
                memoryResult = io.getExtended();
                loadByteRegister(Register.B, io.readByte(memoryResult.get()));
                operationTicks = 5;
                setShortDesc("LDB, EXT [%04X]", memoryResult);
                break;

            /* STB - Store B - Extended */
            case 0xF7:
                memoryResult = io.getExtended();
                storeByteRegister(Register.B, memoryResult.get());
                operationTicks = 5;
                setShortDesc("STB, EXT [%04X]", memoryResult);
                break;

            /* EORB - Exclusive OR B - Extended */
            case 0xF8:
                memoryResult = io.getExtended();
                exclusiveOr(Register.B, io.readByte(memoryResult.get()));
                operationTicks = 5;
                setShortDesc("EORB, EXT [%04X]", memoryResult);
                break;

            /* ADCB - Add with Carry B - Extended */
            case 0xF9:
                memoryResult = io.getExtended();
                addWithCarry(Register.B, io.readByte(memoryResult.get()));
                operationTicks = 5;
                setShortDesc("ADCB, EXT [%04X]", memoryResult);
                break;

            /* ORB - Logical OR B - Extended */
            case 0xFA:
                memoryResult = io.getExtended();
                logicalOr(Register.B, io.readByte(memoryResult.get()));
                operationTicks = 5;
                setShortDesc("ORB, EXT [%04X]", memoryResult);
                break;

            /* ADDB - Add B - Extended */
            case 0xFB:
                memoryResult = io.getExtended();
                addByteRegister(Register.B, io.readByte(memoryResult.get()));
                operationTicks = 5;
                setShortDesc("ADDB, EXT [%04X]", memoryResult);
                break;

            /* LDD - Load D - Extended */
            case 0xFC:
                memoryResult = io.getExtended();
                loadRegister(Register.D, io.readWord(memoryResult.get()));
                operationTicks = 6;
                setShortDesc("LDD, EXT [%04X]", memoryResult);
                break;

            /* STD - Store D - Extended */
            case 0xFD:
                memoryResult = io.getExtended();
                storeWordRegister(Register.D, memoryResult.get());
                operationTicks = 6;
                setShortDesc("STD, EXT [%04X]", memoryResult);
                break;

            /* LDU - Load U - Extended */
            case 0xFE:
                memoryResult = io.getExtended();
                loadRegister(Register.U, io.readWord(memoryResult.get()));
                operationTicks = 6;
                setShortDesc("LDU, EXT [%04X]", memoryResult);
                break;

            /* STD - Store U - Extended */
            case 0xFF:
                memoryResult = io.getExtended();
                storeWordRegister(Register.U, memoryResult.get());
                operationTicks = 6;
                setShortDesc("STU, EXT [%04X]", memoryResult);
                break;

            default:
                throw new RuntimeException("Un-implemented OP code " + operand);
        }

        /* Increment timers if necessary */
        io.timerTick(operationTicks);

        /* Fire interrupts if set */
        if (fireIRQ) {
            interruptRequest();
            fireIRQ = false;
        }

        if (fireFIRQ) {
            fastInterruptRequest();
            fireFIRQ = false;
        }

        if (fireNMI) {
            nonMaskableInterruptRequest();
            fireNMI = false;
        }

        /* Check to see if we should trace the output */
        if (trace) {
            System.out.println(opShortDesc + " : " + opLongDesc);
        }

        return operationTicks;
    }

    /**
     * Executes a byte function on the memory location M, and writes the
     * resultant byte back to the memory location.
     *
     * @param function the function to execute
     * @param memoryResult the MemoryResult where the address is located
     */
    public void executeByteFunctionM(Function<UnsignedByte, UnsignedByte> function,
                                     MemoryResult memoryResult) {
        UnsignedWord address = memoryResult.get();
        UnsignedByte tempByte = io.readByte(address);
        tempByte = function.apply(tempByte);
        io.writeByte(address, tempByte);
    }

    /**
     * Inverts all bits in the byte. Returns the complimented value as the
     * result.
     *
     * @param value the UnsignedByte to complement
     * @return the complimented value
     */
    public UnsignedByte compliment(UnsignedByte value) {
        opLongDesc += "R=" + value + ", ";
        UnsignedByte result = new UnsignedByte(~(value.getShort()));
        opLongDesc += "R'=" + result;
        UnsignedByte cc = io.getCC();
        cc.and(~(IOController.CC_N | IOController.CC_Z | IOController.CC_V));
        cc.or(IOController.CC_C);
        cc.or(result.isNegative() ? IOController.CC_N : 0);
        cc.or(result.isZero() ? IOController.CC_Z : 0);
        return result;
    }

    /**
     * Applies the two's compliment value to the contents in the specified
     * memory address.
     *
     * @param value the byte to negate
     * @return the negated byte
     */
    public UnsignedByte negate(UnsignedByte value) {
        UnsignedByte result = value.twosCompliment();
        UnsignedByte cc = io.getCC();
        cc.and(~(IOController.CC_N | IOController.CC_Z | IOController.CC_V | IOController.CC_C));

        /* Exception cases */
        if (value.equals(new UnsignedByte(0x80))) {
            result = new UnsignedByte(0x80);
            cc.or(IOController.CC_V);
            cc.or(IOController.CC_N);
            cc.or(IOController.CC_C);
        } else if (value.equals(new UnsignedByte(0x0))) {
            result = new UnsignedByte(0x0);
            cc.or(IOController.CC_Z);
        } else {
            cc.or(result.isMasked(0x80) ? IOController.CC_V : 0);
            cc.or(result.isZero() ? IOController.CC_Z : 0);
            cc.or(result.isNegative() ? IOController.CC_N : 0);
            cc.or(IOController.CC_C);
        }
        return result;
    }

    /**
     * Shifts all the bits in the byte to the left by one bit. Returns the
     * result of the operation, while impacting the condition code register.
     * The lowest bit of the byte is shifted into the condition code carry
     * bit.
     *
     * @param value the UnsignedByte to operate on
     * @return the shifted byte value
     */
    public UnsignedByte logicalShiftRight(UnsignedByte value) {
        opLongDesc = "R=" + value + ", C=" + io.ccCarrySet() + ", ";
        UnsignedByte result = new UnsignedByte(value.getShort() >> 1);
        UnsignedByte cc = io.getCC();
        cc.and(~(IOController.CC_N | IOController.CC_Z | IOController.CC_C));
        cc.or(value.isMasked(0x1) ? IOController.CC_C : 0);
        cc.or(result.isZero() ? IOController.CC_Z : 0);
        opLongDesc += "R'=" + result + ", C'=" + io.ccCarrySet();
        return result;
    }

    /**
     * Rotates the bits of a byte one place to the right. Will rotate the
     * carry bit into the highest bit of the byte if set.
     *
     * @param value the value to rotate right
     * @return the rotated value
     */
    public UnsignedByte rotateRight(UnsignedByte value) {
        opLongDesc = "M=" + value + ", C=" + io.ccCarrySet() + ", ";
        UnsignedByte result = new UnsignedByte(value.getShort() >> 1);
        UnsignedByte cc = io.getCC();
        result.add(io.ccCarrySet() ? 0x80 : 0x0);
        cc.and(~(IOController.CC_N | IOController.CC_Z | IOController.CC_C));
        cc.or(value.isMasked(0x1) ? IOController.CC_C : 0);
        cc.or(result.isZero() ? IOController.CC_Z : 0);
        cc.or(result.isNegative() ? IOController.CC_N : 0);
        opLongDesc += "M'=" + result + ", C'=" + io.ccCarrySet();
        return result;
    }

    /**
     * Shifts the bits of a byte one place to the right. Will maintain a copy
     * of bit 7 in the 7th bit. Bit 0 will be shifted into the carry bit.
     *
     * @param value the value to shift right
     * @return the shifted value
     */
    public UnsignedByte arithmeticShiftRight(UnsignedByte value) {
        UnsignedByte result = new UnsignedByte(value.getShort() >> 1);
        UnsignedByte cc = io.getCC();
        result.add(value.isMasked(0x80) ? 0x80 : 0);
        cc.and(~(IOController.CC_N | IOController.CC_Z | IOController.CC_C));
        cc.or(value.isMasked(0x1) ? IOController.CC_C : 0);
        cc.or(result.isZero() ? IOController.CC_Z : 0);
        cc.or(result.isNegative() ? IOController.CC_N : 0);
        opLongDesc = "M=" + value + ", M'=" + result + ", C'=" + (io.ccCarrySet() ? 1 : 0);
        return result;
    }

    /**
     * Shifts the bits of a byte one place to the left. Bit 0 will be filled
     * with a zero, while bit 7 will be shifted into the carry bit.
     *
     * @param value the value to shift left
     * @return the shifted value
     */
    public UnsignedByte arithmeticShiftLeft(UnsignedByte value) {
        UnsignedByte result = new UnsignedByte(value.getShort() << 1);
        UnsignedByte cc = io.getCC();
        cc.and(~(IOController.CC_N | IOController.CC_Z | IOController.CC_V | IOController.CC_C));
        cc.or(value.isMasked(0x80) ? IOController.CC_C : 0);
        boolean bit7 = value.isMasked(0x80);
        boolean bit6 = value.isMasked(0x40);
        cc.or(bit7 ^ bit6 ? IOController.CC_V : 0);
        cc.or(result.isZero() ? IOController.CC_Z : 0);
        cc.or(result.isNegative() ? IOController.CC_N : 0);
        opLongDesc = "M=" + value + ", M'=" + result + ", C'=" + (io.ccCarrySet() ? 1 : 0);
        return result;
    }

    /**
     * Rotates the bits of a byte one place to the left. Will rotate the
     * carry bit into the lowest bit of the byte if set.
     *
     * @param value the value to rotate left
     * @return the rotated value
     */
    public UnsignedByte rotateLeft(UnsignedByte value) {
        opLongDesc = "M=" + value + ", C=" + io.ccCarrySet() + ", ";
        UnsignedByte result = new UnsignedByte(value.getShort() << 1);
        UnsignedByte cc = io.getCC();
        result.add(io.ccCarrySet() ? 0x1 : 0x0);
        cc.and(~(IOController.CC_N | IOController.CC_Z | IOController.CC_C | IOController.CC_V));
        cc.or(value.isMasked(0x80) ? IOController.CC_C : 0);
        boolean bit7 = value.isMasked(0x80);
        boolean bit6 = value.isMasked(0x40);
        cc.or(IOController.CC_V);
        if (bit7 == bit6) {
            cc.and(~IOController.CC_V);
        }
        cc.or(result.isZero() ? IOController.CC_Z : 0);
        cc.or(result.isNegative() ? IOController.CC_N : 0);
        opLongDesc += "M'=" + result + ", C'=" + io.ccCarrySet();
        return result;
    }

    /**
     * Decrements the byte value by one.
     *
     * @param value the byte value to decrement
     * @return the decremented byte value
     */
    public UnsignedByte decrement(UnsignedByte value) {
        UnsignedByte result = io.binaryAdd(value, new UnsignedByte(0xFF), false, false, false);
        UnsignedByte cc = io.getCC();
        cc.and(~(IOController.CC_N | IOController.CC_Z | IOController.CC_V));
        cc.or(value.isZero() ? IOController.CC_V : 0);
        cc.or(result.isZero() ? IOController.CC_Z : 0);
        cc.or(result.isNegative() ? IOController.CC_N : 0);
        opLongDesc = "M'=" + result;
        return result;
    }

    /**
     * Increments the byte value by one.
     *
     * @param value the byte value to increment
     * @return the incremented byte value
     */
    public UnsignedByte increment(UnsignedByte value) {
        UnsignedByte cc = io.getCC();
        cc.and(~(IOController.CC_N | IOController.CC_Z | IOController.CC_V));
        UnsignedByte result = io.binaryAdd(value, new UnsignedByte(0x1), false, false, true);
        cc.or(result.isZero() ? IOController.CC_Z : 0);
        cc.or(result.isNegative() ? IOController.CC_N : 0);
        opLongDesc = "M'=" + result;
        return result;
    }

    /**
     * Tests the byte for zero condition or negative condition.
     *
     * @param value the byte value to test
     * @return the original byte value
     */
    public UnsignedByte test(UnsignedByte value) {
        UnsignedByte cc = io.getCC();
        cc.and(~(IOController.CC_N | IOController.CC_Z | IOController.CC_V));
        cc.or(value.isZero() ? IOController.CC_Z : 0);
        cc.or(value.isNegative() ? IOController.CC_N : 0);
        opLongDesc = "M=" + value + ", Z=" + (value.isZero() ? 1 : 0) + ", N=" + (value.isNegative() ? 1 : 0);
        return value;
    }

    /**
     * Jumps to the specified address.
     *
     * @param address the address to jump to
     */
    public void jump(UnsignedWord address) {
        io.getWordRegister(Register.PC).set(address);
        opLongDesc += "PC'=" + io.getWordRegister(Register.PC);
    }

    /**
     * Jumps to the specified address, pushing the value of the PC onto the S
     * stack before jumping.
     *
     * @param address the address to jump to
     */
    public void jumpToSubroutine(UnsignedWord address) {
        UnsignedWord pc = io.getWordRegister(Register.PC);
        opLongDesc = "S[" + io.getWordRegister(Register.S) + "]=" + pc + ", ";
        io.pushStack(Register.S, pc);
        pc.set(address);
        opLongDesc += "PC'=" + io.getWordRegister(Register.PC);
    }

    /**
     * Clears the specified byte.
     *
     * @param value the value to sg4ClearScreen
     * @return the cleared byte
     */
    public UnsignedByte clear(UnsignedByte value) {
        UnsignedByte cc = io.getCC();
        cc.and(~(IOController.CC_N | IOController.CC_C | IOController.CC_V));
        cc.or(IOController.CC_Z);
        opLongDesc = "M'=" + new UnsignedByte(0);
        return new UnsignedByte(0);
    }

    /**
     * Increments (or decrements) the program counter by the specified amount.
     * Will interpret the UnsignedWord offset as a negative value if the setHigh
     * bit is set.
     *
     * @param offset the amount to offset the program counter
     */
    public void branchLong(UnsignedWord offset) {
        io.getWordRegister(Register.PC).addSigned(offset);
        opLongDesc = "PC'=" + io.getWordRegister(Register.PC);
    }

    /**
     * Increments (or decrements) the program counter by the specified amount.
     * Will interpret the UnsignedByte offset as a negative value if its high
     * bit is set.
     *
     * @param offset the amount to offset the program counter
     */
    public void branchShort(UnsignedByte offset) {
        io.getWordRegister(Register.PC).add(offset.isNegative() ? offset.getSignedShort() : offset.getShort());
        opLongDesc = "PC'=" + io.getWordRegister(Register.PC);
    }

    /**
     * Saves all registers to the stack, and jumps to the memory location
     * read at the specified address.
     *
     * @param offset the offset to read for a jump address
     */
    public void softwareInterrupt(UnsignedWord offset) {
        io.setCCEverything();
        UnsignedWord pc = io.getWordRegister(Register.PC);
        io.pushStack(Register.S, pc);
        io.pushStack(Register.S, io.getWordRegister(Register.U));
        io.pushStack(Register.S, io.getWordRegister(Register.Y));
        io.pushStack(Register.S, io.getWordRegister(Register.X));
        io.pushStack(Register.S, io.getByteRegister(Register.DP));
        io.pushStack(Register.S, io.getByteRegister(Register.B));
        io.pushStack(Register.S, io.getByteRegister(Register.A));
        io.pushStack(Register.S, io.getByteRegister(Register.CC));
        pc.set(io.readWord(offset));
    }

    /**
     * Performs an Interrupt Request (IRQ). Will save the PC, U, Y,
     * X, DP, B, A and CC registers on the stack, and jump to the address
     * stored at $FFF8.
     */
    public void interruptRequest() {
        UnsignedWord pc = io.getWordRegister(Register.PC);
        UnsignedByte cc = io.getCC();
        io.pushStack(Register.S, pc);
        io.pushStack(Register.S, io.getWordRegister(Register.U));
        io.pushStack(Register.S, io.getWordRegister(Register.Y));
        io.pushStack(Register.S, io.getWordRegister(Register.X));
        io.pushStack(Register.S, io.getByteRegister(Register.DP));
        io.pushStack(Register.S, io.getByteRegister(Register.B));
        io.pushStack(Register.S, io.getByteRegister(Register.A));
        cc.or(IOController.CC_E);
        io.pushStack(Register.S, cc);
        io.setCCInterrupt();
        io.setPC(io.readWord(new UnsignedWord(0xFFF8)));
    }

    /**
     * Performs a Fast Interrupt Request (FIRQ). Will save the PC and
     * CC registers on the stack, and jump to the address stored at
     * $FFF6.
     */
    public void fastInterruptRequest() {
        UnsignedWord pc = io.getWordRegister(Register.PC);
        UnsignedByte cc = io.getCC();
        io.pushStack(Register.S, pc);
        cc.and(~IOController.CC_E);
        io.pushStack(Register.S, cc);
        io.setCCFastInterrupt();
        io.setCCInterrupt();
        io.setPC(io.readWord(new UnsignedWord(0xFFF6)));
    }

    /**
     * Performs a Non Maskable Interrupt Request (NMI). Will save the PC, U, Y,
     * X, DP, B, A and CC registers on the stack, and jump to the address
     * stored at $FFFC.
     */
    public void nonMaskableInterruptRequest() {
        UnsignedWord pc = io.getWordRegister(Register.PC);
        UnsignedByte cc = io.getCC();
        io.pushStack(Register.S, pc);
        io.pushStack(Register.S, io.getWordRegister(Register.U));
        io.pushStack(Register.S, io.getWordRegister(Register.Y));
        io.pushStack(Register.S, io.getWordRegister(Register.X));
        io.pushStack(Register.S, io.getByteRegister(Register.DP));
        io.pushStack(Register.S, io.getByteRegister(Register.B));
        io.pushStack(Register.S, io.getByteRegister(Register.A));
        cc.or(IOController.CC_E);
        io.pushStack(Register.S, cc);
        io.setCCInterrupt();
        io.setCCFastInterrupt();
        io.setPC(io.readWord(new UnsignedWord(0xFFFC)));
    }

    /**
     * Compares the two words and sets the appropriate register sets.
     *
     * @param word1 the first word to compare
     * @param word2 the second word to compare
     */
    public UnsignedWord compareWord(UnsignedWord word1, UnsignedWord word2) {
        UnsignedByte cc = io.getCC();
        cc.and(~(IOController.CC_N | IOController.CC_Z | IOController.CC_V | IOController.CC_C));
        UnsignedWord result = io.binaryAdd(word1, word2.twosCompliment(), false, false, true);
        cc.or(word1.getInt() < word2.getInt() ? IOController.CC_C : 0);
        cc.or(result.isZero() ? IOController.CC_Z : 0);
        cc.or(result.isNegative() ? IOController.CC_N : 0);
        opLongDesc = word1 + " vs " + word2 + ", N=" + io.ccNegativeSet() + ", C=" + io.ccCarrySet() + ", V=" + io.ccOverflowSet();
        return result;
    }

    /**
     * Compares the two bytes and sets the appropriate register sets.
     *
     * @param byte1 the first byte to compare
     * @param byte2 the second byte to compare
     */
    public void compareByte(UnsignedByte byte1, UnsignedByte byte2) {
        UnsignedByte cc = io.getCC();
        cc.and(~(IOController.CC_N | IOController.CC_Z | IOController.CC_V | IOController.CC_C));
        UnsignedByte result = io.binaryAdd(byte1, byte2.twosCompliment(), false, false, true);
        cc.or(byte1.getShort() < byte2.getShort() ? IOController.CC_C : 0);
        cc.or(result.isZero() ? IOController.CC_Z : 0);
        cc.or(result.isNegative() ? IOController.CC_N : 0);
        opLongDesc = byte1 + " vs " + byte2;
    }

    /**
     * Loads the word into the specified register.
     *
     * @param registerFlag the register to load
     * @param value the value to load
     */
    public void loadRegister(Register registerFlag, UnsignedWord value) {
        UnsignedWord register = io.getWordRegister(registerFlag);
        UnsignedByte cc = io.getCC();

        /* D is a special register */
        if (registerFlag == Register.D) {
            io.setD(value);
            register = io.getWordRegister(Register.D);
        } else {
            register.set(value);
        }

        cc.and(~(IOController.CC_V | IOController.CC_N | IOController.CC_Z));
        cc.or(register.isZero() ? IOController.CC_Z : 0);
        cc.or(register.isNegative() ? IOController.CC_N : 0);
        opLongDesc = registerFlag + "'=" + value;
    }

    /**
     * Stores the register in the memory location.
     *
     * @param registerFlag the register to store
     * @param address the memory location to write to
     */
    public void storeWordRegister(Register registerFlag, UnsignedWord address) {
        UnsignedWord register = io.getWordRegister(registerFlag);
        UnsignedByte cc = io.getCC();
        io.writeWord(address, register);
        cc.and(~(IOController.CC_V | IOController.CC_N | IOController.CC_Z));
        cc.or(register.isZero() ? IOController.CC_Z : 0);
        cc.or(register.isNegative() ? IOController.CC_N : 0);
        opLongDesc = "M[" + address + "]'=" + register;
    }

    /**
     * Stores the byte register in the memory location.
     *
     * @param registerFlag the byte register to store
     * @param address the memory location to write to
     */
    public void storeByteRegister(Register registerFlag, UnsignedWord address) {
        UnsignedByte register = io.getByteRegister(registerFlag);
        UnsignedByte cc = io.getCC();
        io.writeByte(address, register);
        cc.and(~(IOController.CC_V | IOController.CC_N | IOController.CC_Z));
        cc.or(register.isZero() ? IOController.CC_Z : 0);
        cc.or(register.isNegative() ? IOController.CC_N : 0);
        opLongDesc = "M[" + address + "]'=" + register;
    }

    /**
     * Performs a correction to the A register to transform the value into
     * a proper BCD form.
     */
    public void decimalAdditionAdjust() {
        UnsignedByte cc = io.getByteRegister(Register.CC);
        UnsignedByte a = io.getByteRegister(Register.A);
        int value = io.getByteRegister(Register.A).getShort();
        int mostSignificantNibble = value & 0xF0;
        int leastSignificantNibble = value & 0x0F;
        int adjustment = 0;

        if (io.ccCarrySet() || mostSignificantNibble > 0x90 || (mostSignificantNibble > 0x80 && leastSignificantNibble > 0x09)) {
            adjustment |= 0x60;
        }

        if (io.ccHalfCarrySet() || leastSignificantNibble > 0x09) {
            adjustment |= 0x06;
        }

        mostSignificantNibble = cc.getShort() & IOController.CC_C;
        UnsignedByte result = new UnsignedByte(mostSignificantNibble + adjustment);

        a.set(io.binaryAdd(a, result, false, true, false));
        cc.and(~(IOController.CC_C | IOController.CC_N | IOController.CC_Z));
        cc.or(io.getByteRegister(Register.A).isZero() ? IOController.CC_Z : 0);
        cc.or(!result.isZero() ? IOController.CC_C : 0);
        cc.or(result.isNegative() ? IOController.CC_N : 0);
    }

    /**
     * Loads the specified value into the specified register.
     *
     * @param register the register to load into
     * @param value the value to load
     */
    public void loadEffectiveAddress(Register register, UnsignedWord value) {
        UnsignedWord reg = io.getWordRegister(register);
        UnsignedByte cc = io.getCC();
        reg.set(value);
        if (register == Register.X || register == Register.Y) {
            cc.and(~(IOController.CC_Z));
            cc.or(reg.isZero() ? IOController.CC_Z : 0);
        }
        opLongDesc = register + "'=" + value;
    }

    /**
     * Pushes the values of one or more registers onto the specified stack
     * according to the post byte that is passed. Will return the number
     * of bytes that were pushed onto the stack.
     *
     * @param register the register to use as a stack pointer
     * @param postByte the postbyte containing the registers to push
     * @return the number of bytes pushed
     */
    public int pushStack(Register register, UnsignedByte postByte) {
        int bytes = 0;
        if (postByte.isMasked(0x80)) {
            io.pushStack(register, io.getWordRegister(Register.PC));
            bytes += 2;
            opLongDesc += "PC ";
        }

        if (postByte.isMasked(0x40)) {
            io.pushStack(register, io.getWordRegister(Register.U));
            bytes += 2;
            opLongDesc += "U ";
        }

        if (postByte.isMasked(0x20)) {
            io.pushStack(register, io.getWordRegister(Register.Y));
            bytes += 2;
            opLongDesc += "Y ";
        }

        if (postByte.isMasked(0x10)) {
            io.pushStack(register, io.getWordRegister(Register.X));
            bytes += 2;
            opLongDesc += "X ";
        }

        if (postByte.isMasked(0x08)) {
            io.pushStack(register, io.getByteRegister(Register.DP));
            bytes += 1;
            opLongDesc += "DP ";
        }

        if (postByte.isMasked(0x04)) {
            io.pushStack(register, io.getByteRegister(Register.B));
            bytes += 1;
            opLongDesc += "B ";
        }

        if (postByte.isMasked(0x02)) {
            io.pushStack(register, io.getByteRegister(Register.A));
            bytes += 1;
            opLongDesc += "A ";
        }

        if (postByte.isMasked(0x01)) {
            io.pushStack(register, io.getByteRegister(Register.CC));
            bytes += 1;
            opLongDesc += "CC ";
        }

        return bytes;
    }

    /**
     * Pops bytes from a stack back into registers based on a postbyte
     * value. Will return the number of bytes popped from the stack.
     *
     * @param register the register to use as a stack pointer
     * @param postByte the post byte encoding what registers to use
     * @return the number of bytes popped
     */
    public int popStack(Register register, UnsignedByte postByte) {
        int bytes = 0;

        if (postByte.isMasked(0x01)) {
            UnsignedByte cc = io.getByteRegister(Register.CC);
            cc.set(io.popStack(register));
            bytes += 1;
            opLongDesc += "CC ";
        }

        if (postByte.isMasked(0x02)) {
            UnsignedByte a = io.getByteRegister(Register.A);
            a.set(io.popStack(register));
            bytes += 1;
            opLongDesc += "A ";
        }

        if (postByte.isMasked(0x04)) {
            UnsignedByte b = io.getByteRegister(Register.B);
            b.set(io.popStack(register));
            bytes += 1;
            opLongDesc += "B ";
        }

        if (postByte.isMasked(0x08)) {
            UnsignedByte dp = io.getByteRegister(Register.DP);
            dp.set(io.popStack(register));
            bytes += 1;
            opLongDesc += "DP ";
        }

        if (postByte.isMasked(0x10)) {
            UnsignedWord x = io.getWordRegister(Register.X);
            x.set(
                    new UnsignedWord(
                            io.popStack(register),
                            io.popStack(register)
                    )
            );
            bytes += 2;
            opLongDesc += "X ";
        }

        if (postByte.isMasked(0x20)) {
            UnsignedWord y = io.getWordRegister(Register.Y);
            y.set(
                    new UnsignedWord(
                            io.popStack(register),
                            io.popStack(register)
                    )
            );
            bytes += 2;
            opLongDesc += "Y ";
        }

        if (postByte.isMasked(0x40)) {
            UnsignedWord u = io.getWordRegister(Register.U);
            u.set(
                    new UnsignedWord(
                            io.popStack(register),
                            io.popStack(register)
                    )
            );
            bytes += 2;
            opLongDesc += "U ";
        }

        if (postByte.isMasked(0x80)) {
            UnsignedWord pc = io.getWordRegister(Register.PC);
            pc.set(
                    new UnsignedWord(
                            io.popStack(register),
                            io.popStack(register)
                    )
            );
            bytes += 2;
            opLongDesc += "PC";
        }

        return bytes;
    }

    /**
     * Subtracts the byte value from the specified register.
     *
     * @param register the register to subtract from
     * @param value the byte value to subtract
     */
    public void subtractM(Register register, UnsignedByte value) {
        UnsignedByte reg = io.getByteRegister(register);
        UnsignedByte cc = io.getCC();
        opLongDesc = register + "=" + reg + ", M=" + value;
        cc.and(~(IOController.CC_N | IOController.CC_V | IOController.CC_Z | IOController.CC_C));
        cc.or(reg.getShort() < value.getShort() ? IOController.CC_C : 0);
        reg.set(io.binaryAdd(reg, value.twosCompliment(), false, false, true));
        cc.or(reg.isZero() ? IOController.CC_Z : 0);
        cc.or(reg.isNegative() ? IOController.CC_N : 0);
        opLongDesc += ", " + register + "'=" + reg + ", C'=" + (io.ccCarrySet() ? 1 : 0);
    }

    /**
     * Subtracts the word value from the D register.
     *
     * @param value the word value to subtract
     */
    public void subtractD(UnsignedWord value) {
        UnsignedWord d = io.getWordRegister(Register.D);
        UnsignedByte cc = io.getCC();
        opLongDesc = "D=" + d + ", M=" + value + ", D-M=";
        cc.and(~(IOController.CC_N | IOController.CC_V | IOController.CC_Z | IOController.CC_C));
        cc.or(d.getInt() < value.getInt() ? IOController.CC_C : 0);
        io.setD(io.binaryAdd(d, value.twosCompliment(), false, false, true));
        d = io.getWordRegister(Register.D);
        cc.or(d.isZero() ? IOController.CC_Z : 0);
        cc.or(d.isNegative() ? IOController.CC_N : 0);
        opLongDesc += d;
    }

    /**
     * Subtracts the byte value and sets carry if required.
     *
     * @param register the register to subtract from
     * @param value the byte value to subtract
     */
    public void subtractMC(Register register, UnsignedByte value) {
        UnsignedByte reg = io.getByteRegister(register);
        UnsignedByte cc = io.getCC();
        opLongDesc = register + "=" + reg + ", M=" + value + ", C=" + io.ccCarrySet() + ", " + register + "-M-C=";
        value.add(io.ccCarrySet() ? 1 : 0);
        cc.and(~(IOController.CC_N | IOController.CC_V | IOController.CC_Z | IOController.CC_C));
        cc.or(reg.getShort() < value.getShort() ? IOController.CC_C : 0);
        reg.set(io.binaryAdd(reg, value.twosCompliment(), false, false, true));
        cc.or(reg.isZero() ? IOController.CC_Z : 0);
        cc.or(reg.isNegative() ? IOController.CC_N : 0);
        opLongDesc += reg;
    }

    /**
     * Performs a logical AND of the byte register and the value.
     *
     * @param register the register to AND
     * @param value the byte value to AND
     */
    public void logicalAnd(Register register, UnsignedByte value) {
        UnsignedByte reg = io.getByteRegister(register);
        opLongDesc = register + "=" + reg + ", M=" + value + ", ";
        UnsignedByte cc = io.getCC();
        cc.and(~(IOController.CC_N | IOController.CC_V | IOController.CC_Z));
        reg.and(value.getShort());
        cc.or(reg.isZero() ? IOController.CC_Z : 0);
        cc.or(reg.isNegative() ? IOController.CC_N : 0);
        opLongDesc += register + "'=" + reg;
    }

    /**
     * Performs a logical OR of the byte register and the value.
     *
     * @param register the register to OR
     * @param value the value to OR
     */
    public void logicalOr(Register register, UnsignedByte value) {
        UnsignedByte reg = io.getByteRegister(register);
        UnsignedByte cc = io.getCC();
        cc.and(~(IOController.CC_N | IOController.CC_V | IOController.CC_Z));
        reg.or(value.getShort());
        cc.or(reg.isZero() ? IOController.CC_Z : 0);
        cc.or(reg.isNegative() ? IOController.CC_N : 0);
        opLongDesc = register + "'=" + reg;
    }

    /**
     * Performs an exclusive OR of the register and the byte value.
     *
     * @param register the register to XOR
     * @param value the byte value to XOR
     */
    public void exclusiveOr(Register register, UnsignedByte value) {
        UnsignedByte reg = io.getByteRegister(register);
        UnsignedByte cc = io.getCC();
        opLongDesc = register + "=" + reg + ", M=" + value + ", ";
        cc.and(~(IOController.CC_N | IOController.CC_V | IOController.CC_Z));
        reg.set(new UnsignedByte(reg.getShort() ^ value.getShort()));
        cc.or(reg.isZero() ? IOController.CC_Z : 0);
        cc.or(reg.isNegative() ? IOController.CC_N : 0);
        opLongDesc += register + "'=" + reg;
    }

    /**
     * Loads the specified register with the value.
     *
     * @param register the register to load
     * @param value the value to load
     */
    public void loadByteRegister(Register register, UnsignedByte value) {
        UnsignedByte reg = io.getByteRegister(register);
        UnsignedByte cc = io.getCC();
        cc.and(~(IOController.CC_N | IOController.CC_V | IOController.CC_Z));
        reg.set(value);
        cc.or(reg.isZero() ? IOController.CC_Z : 0);
        cc.or(reg.isNegative() ? IOController.CC_N : 0);
        opLongDesc = register + "'=" + value;
    }

    /**
     * Performs an addition of the specified register and the value together,
     * plus the value of the carry bit (0 or 1). Stores the result in the
     * specified register.
     *
     * @param register the register to perform the addition with
     * @param value the value to add
     */
    public void addWithCarry(Register register, UnsignedByte value) {
        UnsignedByte reg = io.getByteRegister(register);
        UnsignedByte cc = io.getCC();
        opLongDesc = register + "=" + reg + ", M=" + value + ", C=" + io.ccCarrySet() + ", " + register + "'=";
        boolean setCC = false;
        boolean setOverflow = false;
        int result = value.getShort() + (io.ccCarrySet() ? 1 : 0);
        cc.and(~(IOController.CC_N | IOController.CC_V | IOController.CC_Z | IOController.CC_C | IOController.CC_H));
        if (result > 255) {
            result &= 0xFF;
            setCC = true;
            setOverflow = true;
        }
        reg.set(io.binaryAdd(reg, new UnsignedByte(result), true, true, true));
        opLongDesc += reg;
        cc.or(setCC ? IOController.CC_C : 0);
        cc.or(reg.isZero() ? IOController.CC_Z : 0);
        cc.or(reg.isNegative() ? IOController.CC_N : 0);
        cc.or(setOverflow ? IOController.CC_V : 0);
    }

    /**
     * Adds the specified value to the specified register.
     *
     * @param register the register to add
     * @param value the value to add
     */
    public void addByteRegister(Register register, UnsignedByte value) {
        UnsignedByte cc = io.getCC();
        cc.and(~(IOController.CC_N | IOController.CC_V | IOController.CC_Z | IOController.CC_C | IOController.CC_H));
        UnsignedByte reg = io.getByteRegister(register);
        opLongDesc = register + "=" + reg + ", M=" + value + ", " + register + "'=";
        reg.set(io.binaryAdd(reg, value, true, true, true));
        opLongDesc += reg;
        cc.or(reg.isZero() ? IOController.CC_Z : 0);
        cc.or(reg.isNegative() ? IOController.CC_N : 0);
    }

    /**
     * Adds the specified value to the D register.
     *
     * @param value the value to add
     */
    public void addD(UnsignedWord value) {
        UnsignedWord d = io.getWordRegister(Register.D);
        opLongDesc = "D=" + d + ", M=" + value;
        UnsignedByte cc = io.getCC();
        cc.and(~(IOController.CC_N | IOController.CC_V | IOController.CC_Z | IOController.CC_C));
        io.setD(io.binaryAdd(d, value, false, true, true));
        d = io.getWordRegister(Register.D);
        cc.or(d.isZero() ? IOController.CC_Z : 0);
        cc.or(d.isNegative() ? IOController.CC_N : 0);
        opLongDesc += ", D'=" + d;
    }

    /**
     * Schedules an IRQ interrupt to occur.
     */
    public void scheduleIRQ() {
        fireIRQ = true;
    }

    /**
     * Schedules a fast interrupt to occur.
     */
    public void scheduleFIRQ() {
        fireFIRQ = true;
    }

    /**
     * Schedules a non-maskable interrupt to occur.
     */
    public void scheduleNMI() {
        fireNMI = true;
    }

    /**
     * Starts the CPU running.
     */
    public void run() {
        while (alive) {
            try {
                executeInstruction();
            } catch (IllegalIndexedPostbyteException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    /**
     * Sends a kill signal to the CPU thread.
     */
    public void kill() {
        alive = false;
    }
}
