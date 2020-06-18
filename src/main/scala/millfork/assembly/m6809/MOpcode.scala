package millfork.assembly.m6809

import java.util.Locale

import millfork.error.Logger
import millfork.node.Position

/**
  * @author Karol Stasiak
  */

object MFlag extends Enumeration {
  val Z, C, H, N, V = Value
}

object MOpcode extends Enumeration {
  val ABX, ADCA, ADCB, ADDA, ADDB, ADDD, ANDA, ANDB, ANDCC, ASL, ASR,
  BITA, BITB,
  BRA, BRN, BHI, BLS, BCC, BCS, BNE, BEQ, BVC, BVS, BPL, BMI, BGE, BLT, BGT, BLE,
  CLR, CMPA, CMPB, CMPD, CMPS, CMPU, CMPX, CMPY, COMA, COMB, COM, CWAI,
  DAA, DEC,
  EORA, EORB, EXG,
  INC,
  JMP, JSR,
  LDA, LDB, LDD, LDS, LDU, LDX, LDY, LEAS, LEAU, LEAX, LEAY, LSR,
  MUL,
  NEG, NOP,
  ORA, ORB, ORCC,
  PSHS, PSHU, PULS, PULU,
  ROL, ROR, RTI, RTS,
  SBCA, SBCB, SEX, STA, STB, STD, STS, STU, STX, STY, SUBA, SUBB, SUBD, SWI, SWI2, SWI3, SYNC,
  TFR, TST,
  DISCARD_D, DISCARD_X, DISCARD_Y, DISCARD_CC, CHANGED_MEM, BYTE, LABEL = Value

  private val toMap: Map[String, MOpcode.Value] = {
    val notActualOpcodes: Set[MOpcode.Value] = Set(DISCARD_D, DISCARD_X, DISCARD_Y, DISCARD_CC, CHANGED_MEM, BYTE, LABEL)
    values.filterNot(notActualOpcodes).map(o => o.toString -> o).toMap ++ Map("BHS" -> BCC, "BLO" -> BCS, "LSL" -> ASL)
  }
  val NoopDiscard: Set[MOpcode.Value] = Set(DISCARD_D, DISCARD_X, DISCARD_Y, DISCARD_CC)
  val PrefixedBy10: Set[MOpcode.Value] = Set(CMPD, CMPY, LDS, LDY, SWI2) // TODO: branches
  val PrefixedBy11: Set[MOpcode.Value] = Set(CMPS, CMPU, SWI3)
  val Prefixed: Set[MOpcode.Value] = PrefixedBy10 ++ PrefixedBy11
  val CanHaveInherentAccumulator: Set[MOpcode.Value] = Set(ASL, ASR, CLR, COM, DEC, INC, LSR, NEG, ROL, ROR, TST)
  val Branching: Set[MOpcode.Value] = Set(BRA, BRN, BHI, BLS, BCC, BCS, BNE, BEQ, BVC, BVS, BPL, BMI, BGE, BLT, BGT, BLE)
  val ConditionalBranching: Set[MOpcode.Value] = Set(BHI, BLS, BCC, BCS, BNE, BEQ, BVC, BVS, BPL, BMI, BGE, BLT, BGT, BLE)
  val ChangesAAlways: Set[MOpcode.Value] = Set(ADDA, ADCA, SUBA, SBCA, ANDA, ORA, EORA, SEX, DAA)
  val ChangesBAlways: Set[MOpcode.Value] = Set(ADDB, ADCB, SUBB, SBCB, ANDB, ORB, EORB)
  val ChangesDAlways: Set[MOpcode.Value] = Set(ADDD, SUBD, ANDB, ORB, EORB)
  val ReadsAAlways: Set[MOpcode.Value] = Set(ADDD, SUBD, ANDB, ORB, EORB)
  val AccessesWordInMemory: Set[MOpcode.Value] = Set(ADDD, SUBD, LDD, STD, LDX, LDY, LDU, LDS, STX, STY, STU, STS, CMPD, CMPX, CMPY, CMPU, CMPS)
  val AllLinear: Set[MOpcode.Value] = Set(
    ABX, ADCA, ADCB, ADDA, ADDB, ADDD, ANDA, ANDB, ANDCC, ASL, ASR,
    BITA, BITB,
    CLR, CMPA, CMPB, CMPD, CMPS, CMPU, CMPX, CMPY, COMA, COMB, COM, CWAI,
    DAA, DEC,
    EORA, EORB, EXG,
    INC,
    LDA, LDB, LDD, LDS, LDU, LDX, LDY, LEAS, LEAU, LEAX, LEAY, LSR,
    MUL,
    NEG, NOP,
    ORA, ORB, ORCC,
    PSHS, PSHU, PULS, PULU,
    ROL, ROR,
    SBCA, SBCB, SEX, STA, STB, STD, STS, STU, STX, STY, SUBA, SUBB, SUBD, SYNC,
    TFR, TST,
  )
  val ReadsH: Set[MOpcode.Value] = Set(CWAI, DAA)
  val ReadsV: Set[MOpcode.Value] = Set(CWAI, BVC, BVS, BGE, BLT, BGT, BLE)
  val ReadsN: Set[MOpcode.Value] = Set(CWAI, BPL, BMI, BGE, BLT, BGT, BLE)
  val ReadsZ: Set[MOpcode.Value] = Set(CWAI, BEQ, BNE, BHI, BLS, BLE, BGT)
  val ReadsC: Set[MOpcode.Value] = Set(CWAI, BCC, BCS, BHI, BLS, ADCA, ADCB, SBCA, SBCB, ROL, ROR)
  val ChangesC: Set[MOpcode.Value] = Set(
    CWAI, ORCC, ANDCC,
    ADDA, ADDB, ADDD, ADCA, ADCB, DAA,
    ASL, ASR,
    ASL, BITA, BITB, CLR, COM,
    CMPA, CMPB, CMPD, CMPX, CMPY, CMPU, CMPS,
    MUL,
  )
  val PreservesC: Set[MOpcode.Value] = Set(
    ANDA, ANDB, BITA, BITB, DEC, EXG, INC,
    LDA, LDB, LDD, LDX, LDY, LDU, LDS,
    LEAS, LEAX, LEAY, LEAU,
    NOP, ORA, ORB,
    PSHS, PSHU, PULS, PULU,
    STA, STB, STD, STX, STY, STS, STU,
    TFR, TST
  )
  // The following are incomplete:
  val ChangesN: Set[MOpcode.Value] = Set(
    CWAI, ORCC, ANDCC,
    ADDA, ADDB, ADDD, ADCA, ADCB, SEX,
    SUBA, SUBB, SUBD, SBCA, SBCB,
    ASL, ASR, LSR, ROL, ROR,
    INC, DEC, CLR, NEG, COM, TST,
    ORA, ORB, EORA, EORB, ANDA, ANDB,
    LDA, LDB, LDD, LDX, LDY, LDU, LDS,
    STA, STB, STD, STX, STY, STU, STS,
    CMPA, CMPB, CMPD, CMPX, CMPY, CMPU, CMPS,
  )
  val ChangesZ: Set[MOpcode.Value] = Set(
    CWAI, ORCC, ANDCC,
    ADDA, ADDB, ADDD, ADCA, ADCB, DAA, SEX,
    SUBA, SUBB, SUBD, SBCA, SBCB,
    ASL, ASR, LSR, ROL, ROR,
    INC, DEC, CLR, NEG, COM, TST,
    ORA, ORB, EORA, EORB, ANDA, ANDB,
    LDA, LDB, LDD, LDX, LDY, LDU, LDS,
    STA, STB, STD, STX, STY, STU, STS,
    CMPA, CMPB, CMPD, CMPX, CMPY, CMPU, CMPS,
    LEAX, LEAY,
    MUL,
  )
  val ChangesV: Set[MOpcode.Value] = Set(
    CWAI, ORCC, ANDCC,
    ADDA, ADDB, ADDD, ADCA, ADCB, DAA, SEX,
    SUBA, SUBB, SUBD, SBCA, SBCB,
    ASL, ASR, LSR, ROL, ROR,
    INC, DEC, CLR, NEG, COM, TST,
    ORA, ORB, EORA, EORB, ANDA, ANDB,
    LDA, LDB, LDD, LDX, LDY, LDU, LDS,
    STA, STB, STD, STX, STY, STU, STS,
    CMPA, CMPB, CMPD, CMPX, CMPY, CMPU, CMPS,
  )
  val ChangesH: Set[MOpcode.Value] = Set(
    CWAI, ORCC, ANDCC,
    ADDA, ADDB, ADCA, ADCB,
    SUBA, SUBB, SBCA, SBCB,
    ASL, ASR, LSR, ROL, ROR,
    NEG
  )

  def lookup(opcode: String, position: Some[Position], log: Logger): (MOpcode.Value, Option[MAddrMode]) = {
    val o = opcode.toUpperCase(Locale.ROOT)
    System.out.flush()
    if (o.startsWith("LB") && toMap.contains(o.substring(1))) {
      toMap(o.substring(1)) -> Some(LongRelative)
    } else if (o.endsWith("A") && toMap.contains(o.init) && CanHaveInherentAccumulator(toMap(o.init))) {
      toMap(o.init) -> Some(InherentA)
    } else if (o.endsWith("B") && toMap.contains(o.init) && CanHaveInherentAccumulator(toMap(o.init))) {
      toMap(o.init) -> Some(InherentB)
    } else if (toMap.contains(o)) {
      toMap(o) -> None
    } else {
      log.error(s"Invalid opcode: $o", position)
      NOP -> None
    }
  }
}
