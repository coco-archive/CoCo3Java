/*
 * Copyright (C) 2017 Craig Thomas
 * This project uses an MIT style license - see LICENSE for details.
 */
package ca.craigthomas.yacoco3e.components;

import ca.craigthomas.yacoco3e.datatypes.*;

public class IOController
{
    /* The number of IO memory addresses */
    public final static int IO_ADDRESS_SIZE = 256;

    /* The IO memory address space */
    protected short [] ioMemory;

    protected Memory memory;
    protected RegisterSet regs;
    protected Keyboard keyboard;

    /* Condition Code - Carry */
    public static final short CC_C = 0x01;

    /* Condition Code - Overflow */
    public static final short CC_V = 0x02;

    /* Condition Code - Zero */
    public static final short CC_Z = 0x04;

    /* Condition Code - Negative */
    public static final short CC_N = 0x08;

    /* Condition Code - Interrupt Request */
    public static final short CC_I = 0x10;

    /* Condition Code - Half Carry */
    public static final short CC_H = 0x20;

    /* Condition Code - Fast Interrupt Request */
    public static final short CC_F = 0x40;

    /* Condition Code - Everything */
    public static final short CC_E = 0x80;

    public IOController(Memory memory, RegisterSet registerSet, Keyboard keyboard) {
        ioMemory = new short[IO_ADDRESS_SIZE];
        this.memory = memory;
        this.regs = registerSet;
        this.keyboard = keyboard;
    }

    /**
     * Reads an UnsignedByte from the specified address.
     *
     * @param address the UnsignedWord location to read from
     * @return an UnsignedByte from the specified location
     */
    public UnsignedByte readByte(UnsignedWord address) {
        int intAddress = address.getInt();
        if (intAddress < 0xFF00) {
            return memory.readByte(address);
        }
        return readIOByte(intAddress);
    }

    /**
     * Reads an IO byte from memory.
     *
     * @param address the address to read from
     * @return the IO byte read
     */
    public UnsignedByte readIOByte(int address) {
        switch (address) {
            /* Keyboard High Byte */
            case 0xFF00:
                return keyboard.getHighByte();

            /* Keyboard Low Byte */
            case 0xFF02:
                return keyboard.getLowByte();

            /* Reset Interrupt Vector - High */
            case 0xFFFE:
                return new UnsignedByte(0x8C);

            /* Reset Interrupt Vector - Low */
            case 0xFFFF:
                return new UnsignedByte(0x1B);
        }

        return new UnsignedByte(ioMemory[address - 0xFF00]);
    }

    /**
     * Reads an UnsignedWord from the specified address.
     *
     * @param address the UnsignedWord location to read from
     * @return an UnsignedWord from the specified location
     */
    public UnsignedWord readWord(UnsignedWord address) {
        UnsignedWord result = new UnsignedWord();
        result.setHigh(readByte(address));
        result.setLow(readByte(address.next()));
        return result;
    }

    /**
     * Writes an UnsignedByte to the specified memory address.
     *
     * @param address the UnsignedWord location to write to
     * @param value the UnsignedByte to write
     */
    public void writeByte(UnsignedWord address, UnsignedByte value) {
        int intAddress = address.getInt();
        if (intAddress < 0xFF00) {
            memory.writeByte(address, value);
        } else {
            writeIOByte(address, value);
        }
    }

    /**
     * Writes an UnsignedByte to the specified memory address.
     *
     * @param address the address in IO space to write to
     * @param value the value to write
     */
    public void writeIOByte(UnsignedWord address, UnsignedByte value) {
        int intAddress = address.getInt() - 0xFF00;
        ioMemory[intAddress] = value.getShort();

        switch (address.getInt()) {

            /* INIT 0 */
            case 0xFF90:
                /* ROM memory mapping - two lowest bits */
                memory.setROMMode(new UnsignedByte(value.getShort() & 0x3));

                /* MMU - disable or enable */
                if (value.isMasked(0x40)) {
                    memory.enableMMU();
                } else {
                    memory.disableMMU();
                }
                break;

            /* INIT 1 */
            case 0xFF91:
                /* PAR selection - Task or Executive */
                if (value.isMasked(0x1)) {
                    memory.enableExecutivePAR();
                } else {
                    memory.enableTaskPAR();
                }
                break;

            /* EXEC PAR 0 */
            case 0xFFA0:
                memory.setExecutivePAR(0, value);
                break;

            /* EXEC PAR 1 */
            case 0xFFA1:
                memory.setExecutivePAR(1, value);
                break;

            /* EXEC PAR 2 */
            case 0xFFA2:
                memory.setExecutivePAR(2, value);
                break;

            /* EXEC PAR 3 */
            case 0xFFA3:
                memory.setExecutivePAR(3, value);
                break;

            /* EXEC PAR 4 */
            case 0xFFA4:
                memory.setExecutivePAR(4, value);
                break;

            /* EXEC PAR 5 */
            case 0xFFA5:
                memory.setExecutivePAR(5, value);
                break;

            /* EXEC PAR 6 */
            case 0xFFA6:
                memory.setExecutivePAR(6, value);
                break;

            /* EXEC PAR 7 */
            case 0xFFA7:
                memory.setExecutivePAR(7, value);
                break;

            /* TASK PAR 0 */
            case 0xFFA8:
                memory.setTaskPAR(0, value);
                break;

            /* TASK PAR 1 */
            case 0xFFA9:
                memory.setTaskPAR(1, value);
                break;

            /* TASK PAR 2 */
            case 0xFFAA:
                memory.setTaskPAR(2, value);
                break;

            /* TASK PAR 3 */
            case 0xFFAB:
                memory.setTaskPAR(3, value);
                break;

            /* TASK PAR 4 */
            case 0xFFAC:
                memory.setTaskPAR(4, value);
                break;

            /* TASK PAR 5 */
            case 0xFFAD:
                memory.setTaskPAR(5, value);
                break;

            /* TASK PAR 6 */
            case 0xFFAE:
                memory.setTaskPAR(6, value);
                break;

            /* TASK PAR 7 */
            case 0xFFAF:
                memory.setTaskPAR(7, value);
                break;

            /* Clear SAM TY Bit - ROM/RAM mode */
            case 0xFFDE:
                memory.disableAllRAMMode();
                break;

            /* Set SAM TY Bit - all RAM mode */
            case 0xFFDF:
                memory.enableAllRAMMode();
                break;

            /* Reset Interrupt Vector */
            case 0xFFFE:
                break;
        }
    }

    /**
     * Writes an UnsignedWord to the specified memory address.
     *
     * @param address the UnsignedWord location to write to
     * @param value the UnsignedWord to write
     */
    public void writeWord(UnsignedWord address, UnsignedWord value) {
        writeByte(address, value.getHigh());
        writeByte(address.next(), value.getLow());
    }

    /**
     * Pushes the specified byte onto the specified stack. Will decrement the
     * stack pointer prior to performing the push.
     *
     * @param register the stack to use
     * @param value the value to push
     */
    public void pushStack(Register register, UnsignedByte value) {
        if (register == Register.S) {
            regs.getS().add(-1);
            writeByte(regs.getS(), value);
        } else {
            regs.getU().add(-1);
            writeByte(regs.getU(), value);
        }
    }

    /**
     * Pushes the specified word onto the specified stack. Will decrement the
     * stack pointer prior to performing the push.
     *
     * @param register the stack to use
     * @param value the value to push
     */
    public void pushStack(Register register, UnsignedWord value) {
        pushStack(register, value.getLow());
        pushStack(register, value.getHigh());
    }

    /**
     * Pops a byte off of a stack. Will increment the stack pointer after
     * performing the pop.
     *
     * @param register the stack to use
     * @return the value popped
     */
    public UnsignedByte popStack(Register register) {
        UnsignedByte result = new UnsignedByte();
        if (register == Register.S) {
            result.set(readByte(regs.getS()));
            regs.getS().add(1);
        } else {
            result.set(readByte(regs.getU()));
            regs.getU().add(1);
        }
        return result;
    }

    /**
     * Reads a single byte at the current value that is the program
     * counter. Will store the byte in the high byte of the resultant
     * word.
     *
     * @return a MemoryResult with the data from the PC location
     */
    public MemoryResult getImmediateByte() {
        UnsignedByte theByte = readByte(regs.getPC());
        regs.incrementPC();
        return new MemoryResult(
                1,
                new UnsignedWord(theByte, new UnsignedByte())
        );
    }

    /**
     * Given the current registers, will return the value that is
     * pointed to by the program counter.
     *
     * @return a MemoryResult with the data from the PC location
     */
    public MemoryResult getImmediateWord() {
        UnsignedWord theWord = readWord(regs.getPC());
        regs.incrementPC();
        regs.incrementPC();
        return new MemoryResult(
                2,
                theWord
        );
    }

    /**
     * Given the current registers, will return an UnsignedWord from
     * the memory location of the direct pointer as the setHigh byte,
     * and the setLow byte pointed to by the PC.
     *
     * @return a MemoryResult with the data from the DP:PC location
     */
    public MemoryResult getDirect() {
        UnsignedByte lowByte = readByte(regs.getPC());
        regs.incrementPC();
        return new MemoryResult(
                1,
                new UnsignedWord(regs.getDP(), lowByte)
        );
    }

    /**
     * Returns the register based on the post byte code.
     *
     * @param postByte the post byte to decode
     * @return the specified register, UNKNOWN if not a valid code
     */
    public Register getIndexedRegister(UnsignedByte postByte) {
        int value = postByte.getShort();
        value &= 0x60;
        value = value >> 5;

        switch (value) {
            case 0x0:
                return Register.X;

            case 0x1:
                return Register.Y;

            case 0x2:
                return Register.U;

            case 0x3:
                return Register.S;
        }
        return Register.UNKNOWN;
    }

    /**
     * The getIndexed function reads the byte following the current PC word, and
     * interprets the byte. Depending on the value of the byte, a new value is
     * returned. May throw an IllegalIndexedPostbyteException.
     *
     * @return a new MemoryResult with the indexed value
     */
    public MemoryResult getIndexed() throws IllegalIndexedPostbyteException {
        UnsignedByte postByte = readByte(regs.getPC());
        regs.incrementPC();
        UnsignedWord r;
        UnsignedByte a;
        UnsignedByte b;
        UnsignedWord d;
        UnsignedByte nByte;
        UnsignedWord nWord;
        UnsignedWord result;

        /* 5-bit offset - check for signed values */
        if (!postByte.isMasked(0x80)) {
            r = getWordRegister(getIndexedRegister(postByte));
            UnsignedByte offset = new UnsignedByte(postByte.getShort() & 0x1F);
            if (offset.isMasked(0x10)) {
                offset.and(0xF);
                UnsignedByte newOffset = offset.twosCompliment();
                newOffset.and(0xF);
                System.out.print(" (new 5-bit offset = -" + newOffset + ", R = " + r.toString() + ") ");
                result = new UnsignedWord(r.getInt() - newOffset.getShort());
            } else {
                result = new UnsignedWord(r.getInt() + offset.getShort());
            }
            return new MemoryResult(1, result);
        }

        switch (postByte.getShort() & 0x1F) {
            /* ,R+ -> R, then increment R */
            case 0x00:
                r = getWordRegister(getIndexedRegister(postByte));
                result = new UnsignedWord(r.getInt());
                r.add(1);
                return new MemoryResult(1, result);

            /* ,R++ -> R, then increment R by two */
            case 0x01:
                r = getWordRegister(getIndexedRegister(postByte));
                result = new UnsignedWord(r.getInt());
                r.add(2);
                return new MemoryResult(1, result);

            /* ,R- -> R, then decrement */
            case 0x02:
                r = getWordRegister(getIndexedRegister(postByte));
                result = new UnsignedWord(r.getInt());
                r.add(-1);
                return new MemoryResult(1, result);

            /* ,R-- -> R, then decrement by two */
            case 0x03:
                r = getWordRegister(getIndexedRegister(postByte));
                result = new UnsignedWord(r.getInt());
                r.add(-2);
                return new MemoryResult(1, result);

            /* ,R -> No offset, just R */
            case 0x04:
                r = getWordRegister(getIndexedRegister(postByte));
                return new MemoryResult(1, r.copy());

            /* B,R -> B offset from R */
            case 0x05:
                r = getWordRegister(getIndexedRegister(postByte));
                b = getByteRegister(Register.B);
                result = new UnsignedWord(r.getInt() + b.getSignedShort());
                return new MemoryResult(1, result);

            /* A,R -> A offset from R */
            case 0x06:
                r = getWordRegister(getIndexedRegister(postByte));
                a = getByteRegister(Register.A);
                result = new UnsignedWord(r.getInt() + a.getSignedShort());
                return new MemoryResult(1, result);

            /* n,R -> 8-bit offset from R */
            case 0x08:
                r = getWordRegister(getIndexedRegister(postByte));
                nByte = readByte(regs.getPC());
                regs.incrementPC();
                result = new UnsignedWord(r.getInt() + nByte.getSignedShort());
                return new MemoryResult(2, result);

            /* n,R -> 16-bit offset from R */
            case 0x09:
                r = getWordRegister(getIndexedRegister(postByte));
                nWord = readWord(regs.getPC());
                regs.incrementPC();
                regs.incrementPC();
                result = new UnsignedWord(r.getInt() + nWord.getSignedInt());
                return new MemoryResult(3, result);

            /* D,R -> D offset from R */
            case 0x0B:
                r = getWordRegister(getIndexedRegister(postByte));
                d = getWordRegister(Register.D);
                result = new UnsignedWord(r.getInt() + d.getSignedInt());
                return new MemoryResult(1, result);

            /* n,PC -> 8-bit offset from PC */
            case 0x0C:
                r = getWordRegister(Register.PC);
                nByte = readByte(regs.getPC());
                regs.incrementPC();
                result = new UnsignedWord(r.getInt() + nByte.getSignedShort());
                return new MemoryResult(2, result);

            /* n,PC -> 16-bit offset from PC */
            case 0x0D:
                r = getWordRegister(Register.PC);
                nWord = readWord(regs.getPC());
                regs.incrementPC();
                regs.incrementPC();
                result = new UnsignedWord(r.getInt() + nWord.getSignedInt());
                return new MemoryResult(3, result);

            /* [,R++] -> R, then increment R by two - indirect*/
            case 0x11:
                r = getWordRegister(getIndexedRegister(postByte));
                result = readWord(new UnsignedWord(r.getInt()));
                r.add(2);
                return new MemoryResult(1, result);

            /* [,R--] -> R, then decrement by two - indirect*/
            case 0x13:
                r = getWordRegister(getIndexedRegister(postByte));
                result = readWord(new UnsignedWord(r.getInt()));
                r.add(-2);
                return new MemoryResult(1, result);

            /* [,R] -> No offset, just R - indirect */
            case 0x14:
                r = readWord(getWordRegister(getIndexedRegister(postByte)));
                return new MemoryResult(1, r);

            /* [B,R] -> B offset from R - indirect */
            case 0x15:
                r = getWordRegister(getIndexedRegister(postByte));
                b = getByteRegister(Register.B);
                result = readWord(new UnsignedWord(r.getInt() + b.getSignedShort()));
                return new MemoryResult(1, result);

            /* [A,R] -> A offset from R - indirect */
            case 0x16:
                r = getWordRegister(getIndexedRegister(postByte));
                a = getByteRegister(Register.A);
                result = readWord(new UnsignedWord(r.getInt() + a.getSignedShort()));
                return new MemoryResult(1, result);

            /* [n,R] -> 8-bit offset from R - indirect */
            case 0x18:
                r = getWordRegister(getIndexedRegister(postByte));
                nByte = readByte(regs.getPC());
                regs.incrementPC();
                result = readWord(new UnsignedWord(r.getInt() + nByte.getSignedShort()));
                return new MemoryResult(2, result);

            /* [n,R] -> 16-bit offset from R - indirect */
            case 0x19:
                r = getWordRegister(getIndexedRegister(postByte));
                nWord = readWord(regs.getPC());
                regs.incrementPC();
                regs.incrementPC();
                result = readWord(new UnsignedWord(r.getInt() + nWord.getSignedInt()));
                return new MemoryResult(3, result);

            /* [D,R] -> D offset from R - indirect*/
            case 0x1B:
                r = getWordRegister(getIndexedRegister(postByte));
                d = getWordRegister(Register.D);
                result = readWord(new UnsignedWord(r.getInt() + d.getSignedInt()));
                return new MemoryResult(1, result);

            /* [n,PC] -> 8-bit offset from PC - indirect */
            case 0x1C:
                r = getWordRegister(Register.PC);
                nByte = readByte(regs.getPC());
                regs.incrementPC();
                result = readWord(new UnsignedWord(r.getInt() + nByte.getSignedShort()));
                return new MemoryResult(2, result);

            /* [n,PC] -> 16-bit offset from PC - indirect */
            case 0x1D:
                r = getWordRegister(Register.PC);
                nWord = readWord(regs.getPC());
                regs.incrementPC();
                regs.incrementPC();
                result = readWord(new UnsignedWord(r.getInt() + nWord.getSignedInt()));
                return new MemoryResult(3, result);

            /* [n] -> extended indirect */
            case 0x1F:
                nWord = readWord(regs.getPC());
                regs.incrementPC();
                regs.incrementPC();
                result = readWord(nWord);
                return new MemoryResult(3, result);
        }

        throw new IllegalIndexedPostbyteException(postByte);
    }

    /**
     * Given the current registers, will return the value that is
     * pointed to by the value that is pointed to by the program
     * counter value.
     *
     * @return a MemoryResult with the data from the PC location
     */
    public MemoryResult getExtended() {
        UnsignedWord address = readWord(regs.getPC());
        regs.incrementPC();
        regs.incrementPC();
        return new MemoryResult(
                2,
                address
        );
    }

    /**
     * Returns the byte that immediately available by the program counter.
     *
     * @return the current program counter byte
     */
    public UnsignedByte getPCByte() {
        return readByte(regs.getPC());
    }

    /**
     * Increments the Program Counter by 1 byte.
     */
    public void incrementPC() {
        regs.incrementPC();
    }

    /**
     * Returns the Condition Code register.
     *
     * @return the Condition Code register
     */
    public UnsignedByte getCC() {
        return regs.getCC();
    }

    /**
     * Gets the byte register of the specified type.
     *
     * @param register the register to get
     * @return the register
     */
    public UnsignedByte getByteRegister(Register register) {
        switch (register) {
            case A:
                return regs.getA();

            case B:
                return regs.getB();

            case DP:
                return regs.getDP();

            case CC:
                return regs.getCC();
        }

        return null;
    }

    /**
     * Gets the register of the specified type.
     *
     * @param register the register to get
     * @return the register
     */
    public UnsignedWord getWordRegister(Register register) {
        switch (register) {
            case Y:
                return regs.getY();

            case X:
                return regs.getX();

            case S:
                return regs.getS();

            case U:
                return regs.getU();

            case D:
                return regs.getD();

            case PC:
                return regs.getPC();
        }

        return null;
    }

    public boolean ccCarrySet() {
        return regs.getCC().isMasked(CC_C);
    }

    public void setCCCarry() {
        regs.getCC().or(CC_C);
    }

    public boolean ccOverflowSet() {
        return regs.getCC().isMasked(CC_V);
    }

    public void setCCOverflow() {
        regs.getCC().or(CC_V);
    }

    public boolean ccZeroSet() {
        return regs.getCC().isMasked(CC_Z);
    }

    public void setCCZero() {
        regs.getCC().or(CC_Z);
    }

    public boolean ccNegativeSet() {
        return regs.getCC().isMasked(CC_N);
    }

    public void setCCNegative() {
        regs.getCC().or(CC_N);
    }

    public boolean ccInterruptSet() {
        return regs.getCC().isMasked(CC_I);
    }

    public void setCCInterrupt() {
        regs.getCC().or(CC_I);
    }

    public boolean ccHalfCarrySet() {
        return regs.getCC().isMasked(CC_H);
    }

    public void setCCHalfCarry() {
        regs.getCC().or(CC_H);
    }

    public boolean ccFastInterruptSet() {
        return regs.getCC().isMasked(CC_F);
    }

    public void setCCFastInterrupt() {
        regs.getCC().or(CC_F);
    }

    public void setCCEverything() {
        regs.getCC().or(CC_E);
    }

    public boolean ccEverythingSet() {
        return regs.getCC().isMasked(CC_E);
    }

    /**
     * Performs a binary add of the two values, setting flags on the condition
     * code register where required.
     *
     * @param val1 the first value to add
     * @param val2 the second value to add
     * @param flagHalfCarry whether to flag half carries
     * @param flagCarry whether to flag full carries
     * @param flagOverflow whether to flag overflow
     *
     * @return the addition of the two values
     */
    public UnsignedWord binaryAdd(UnsignedWord val1, UnsignedWord val2,
                                  boolean flagHalfCarry, boolean flagCarry,
                                  boolean flagOverflow) {
        int value1 = val1.getInt();
        int value2 = val2.getInt();

        /* Check to see if a half carry occurred and we should flag it */
        if (flagHalfCarry) {
            UnsignedWord test = new UnsignedWord(value1 & 0xF);
            test.add(value2 & 0xF);
            if (test.isMasked(0x10)) {
                setCCHalfCarry();
            }
        }

        /* Check to see if a full carry occurred and we should flag it */
        if (flagCarry) {
            if (((value1 + value2) & 0x10000) > 0) {
                setCCCarry();
            }
        }

        /* Check to see if overflow occurred and we should flag it */
        if (flagOverflow) {
            int signedResult = val1.getSignedInt() + val2.getSignedInt();
            if (signedResult > 32767 || signedResult < -32767) {
                setCCOverflow();
            }
        }

        return new UnsignedWord(value1 + value2);
    }

    /**
     * Performs a binary add of the two values, setting flags on the condition
     * code register where required.
     *
     * @param val1 the first value to add
     * @param val2 the second value to add
     * @param flagHalfCarry whether to flag half carries
     * @param flagCarry whether to flag full carries
     * @param flagOverflow whether to flag overflow
     *
     * @return the addition of the two values
     */
    public UnsignedByte binaryAdd(UnsignedByte val1, UnsignedByte val2,
                                  boolean flagHalfCarry, boolean flagCarry,
                                  boolean flagOverflow) {
        int value1 = val1.getShort();
        int value2 = val2.getShort();

        /* Check for half carries */
        if (flagHalfCarry) {
            UnsignedByte test = new UnsignedByte(value1 & 0xF);
            test.add(value2 & 0xF);
            if (test.isMasked(0x10)) {
                setCCHalfCarry();
            }
        }

        /* Check for full carries */
        if (flagCarry) {
            if (((value1 + value2) & 0x100) > 0) {
                setCCCarry();
            }
        }

        /* Check for overflow */
        if (flagOverflow) {
            int signedResult = val1.getSignedShort() + val2.getSignedShort();
            if (signedResult > 127 || signedResult < -127) {
                setCCOverflow();
            }
        }

        return new UnsignedByte(value1 + value2);
    }

    public void setA(UnsignedByte a) {
        regs.setA(a);
    }

    public void setB(UnsignedByte b) {
        regs.setB(b);
    }

    public void setCC(UnsignedByte cc) {
        regs.setCC(cc);
    }

    public void setDP(UnsignedByte dp) {
        regs.setDP(dp);
    }

    public void setX(UnsignedWord x) {
        regs.setX(x);
    }

    public void setY(UnsignedWord y) {
        regs.setY(y);
    }

    public void setU(UnsignedWord u) {
        regs.setU(u);
    }

    public void setS(UnsignedWord s) {
        regs.setS(s);
    }

    public void setD(UnsignedWord d) {
        regs.setD(d);
    }

    public void setPC(UnsignedWord pc) {
        regs.setPC(pc);
    }

    /**
     * Resets the computer state.
     */
    public void reset() {
        /* Reset Condition Code register */
        regs.setCC(new UnsignedByte(0));
        regs.cc.or(IOController.CC_I);
        regs.cc.or(IOController.CC_F);

        /* Load PC with Reset Interrupt Vector */
        regs.setPC(new UnsignedWord(0xC000));

        /* Disable MMU */
        memory.disableMMU();

        /* Set ROM/RAM mode */
        memory.setROMMode(new UnsignedByte(0x2));
        memory.disableAllRAMMode();
    }
}
