package millfork.assembly.mos.opt

import millfork.assembly.mos.Opcode
import millfork.assembly.mos.Opcode._
import millfork.assembly.opt.{AnyStatus, SingleStatus, Status}

/**
  * @author Karol Stasiak
  */
object FlowAnalyzerForImmediate {
  private val map: Map[Opcode.Value, (Int, CpuStatus) => CpuStatus] = Map(
    NOP -> ((_, c) => c),
    REP -> {(nn, c) =>
      var currentStatus = c
      if ((nn & 1) != 0) currentStatus = currentStatus.copy(c = Status.SingleFalse)
      if ((nn & 2) != 0) currentStatus = currentStatus.copy(z = Status.SingleFalse)
      if ((nn & 8) != 0) currentStatus = currentStatus.copy(d = Status.SingleFalse)
      if ((nn & 0x10) != 0) currentStatus = currentStatus.copy(w = Status.SingleFalse, eqSX = false, eqSpX = false)
      if ((nn & 0x20) != 0) currentStatus = currentStatus.copy(m = Status.SingleFalse)
      if ((nn & 0x40) != 0) currentStatus = currentStatus.copy(v = Status.SingleFalse)
      if ((nn & 0x80) != 0) currentStatus = currentStatus.copy(n = Status.SingleFalse)
      currentStatus
    },
    SEP -> {(nn, c) =>
      var currentStatus = c
      if ((nn & 1) != 0) currentStatus = currentStatus.copy(c = Status.SingleTrue)
      if ((nn & 2) != 0) currentStatus = currentStatus.copy(z = Status.SingleTrue)
      if ((nn & 8) != 0) currentStatus = currentStatus.copy(d = Status.SingleTrue)
      if ((nn & 0x10) != 0) currentStatus = currentStatus.copy(w = Status.SingleTrue, eqSX = false, eqSpX = false)
      if ((nn & 0x20) != 0) currentStatus = currentStatus.copy(m = Status.SingleTrue)
      if ((nn & 0x40) != 0) currentStatus = currentStatus.copy(v = Status.SingleTrue)
      if ((nn & 0x80) != 0) currentStatus = currentStatus.copy(n = Status.SingleTrue)
      currentStatus
    },
    LDX -> {(nn, currentStatus) =>
      val n = nn & 0xff
      currentStatus.nz(n).copy(x = SingleStatus(n), src = SourceOfNZ.X, eqSX = false, eqSpX = false)
    },
    LDY -> {(nn, currentStatus) =>
      val n = nn & 0xff
      currentStatus.nz(n).copy(y = SingleStatus(n), src = SourceOfNZ.Y)
    },
    LDA -> {(nn, currentStatus) =>
      val n = nn & 0xff
      currentStatus.nz(n).copy(
        a = SingleStatus(n),
        a7 = SingleStatus((n & 0x80) != 0),
        a0 = SingleStatus((n & 1) != 0),
        src = SourceOfNZ.A)
    },
    LDZ -> {(nn, currentStatus) =>
      val n = nn & 0xff
      currentStatus.nz(n).copy(iz = SingleStatus(n), src = SourceOfNZ.Z)
    },
    LDX_W -> {(nn, currentStatus) =>
      val n = nn & 0xff
      currentStatus.nzw(nn).copy(x = SingleStatus(n), src = AnyStatus, eqSX = false, eqSpX = false)
    },
    LDY_W -> {(nn, currentStatus) =>
      val n = nn & 0xff
      currentStatus.nzw(nn).copy(y = SingleStatus(n), src = AnyStatus)
    },
    LDA_W -> {(nn, currentStatus) =>
      val n = nn & 0xff
      val nh = (nn >> 8) & 0xff
      currentStatus.nzw(nn).copy(
        a = SingleStatus(n),
        a7 = SingleStatus((n & 0x80) != 0),
        a0 = SingleStatus((n & 1) != 0),
        ah = SingleStatus(nh),
        src = SourceOfNZ.AW)
    },
    ADC -> {(nn, currentStatus) =>
      val n = nn & 0xff
      val (newA, newC) = currentStatus.a.adc(n, currentStatus.c, currentStatus.d)
      currentStatus.copy(
        n = newA.n(),
        z = newA.z(),
        src = currentStatus.d.flatMap((dec: Boolean) => if (dec) AnyStatus else SourceOfNZ.A),
        a = newA,
        a0 = newA.bit0,
        a7 = newA.bit7,
        c = newC | Status.flatMap3(currentStatus.a, currentStatus.c, currentStatus.d) {
          case (aa, false, false) => SingleStatus((aa & 0xff) + n >= 0x100)
          case _ => AnyStatus
        },
        v = AnyStatus)
    },
    SBC -> {(nn, currentStatus) =>
      val n = nn & 0xff
      val (newA, newC) = currentStatus.a.sbc(n, currentStatus.c, currentStatus.d)
      currentStatus.copy(
        n = newA.n(),
        z = newA.z(),
        src = currentStatus.d.flatMap((dec: Boolean) => if (dec) AnyStatus else SourceOfNZ.A),
        a = newA,
        a0 = newA.bit0,
        a7 = newA.bit7,
        c = newC,
        v = AnyStatus)
    },
    EOR -> {(nn, currentStatus) =>
      val n = nn & 0xff
      val newA = currentStatus.a.map(_ ^ n)
      currentStatus.copy(
        n = newA.n(),
        z = newA.z(),
        a = newA,
        a7 = if ((nn & 0x80) != 0) currentStatus.a7.map(!_) else currentStatus.a7,
        a0 = if ((nn & 1) != 0) currentStatus.a0.map(!_) else currentStatus.a0,
        src = SourceOfNZ.A)
    },
    AND -> {(nn, currentStatus) =>
      val n = nn & 0xff
      val newA = if (n == 0) Status.SingleZero else currentStatus.a.map(_ & n)
      val newN = if (n <= 0x7f) Status.SingleFalse else newA.n()
      currentStatus.copy(
        n = newN,
        z = newA.z(),
        a = newA,
        a7 = if ((nn & 0x80) != 0) newA.bit7 else Status.SingleFalse,
        a0 = if ((nn & 1) != 0) newA.bit0 else Status.SingleFalse,
        src = SourceOfNZ.A)
    },
    ANC -> {(nn, currentStatus) =>
      val n = nn & 0xff
      val newA = if (n == 0) Status.SingleZero else currentStatus.a.map(_ & n)
      val newNC = if (n <= 0x7f) Status.SingleFalse else newA.n()
      currentStatus.copy(
        n = newNC,
        c = newNC,
        z = newA.z(),
        a = newA,
        a7 = if ((nn & 0x80) != 0) newA.bit7 else Status.SingleFalse,
        a0 = if ((nn & 1) != 0) newA.bit0 else Status.SingleFalse,
        src = SourceOfNZ.A)
    },
    ORA -> {(nn, currentStatus) =>
      val n = nn & 0xff
      val newA = currentStatus.a.map(_ | n)
      currentStatus.copy(
        n = newA.n(),
        z = newA.z(),
        a = newA,
        a7 = if ((nn & 0x80) == 0) currentStatus.a7 else Status.SingleTrue,
        a0 = if ((nn & 1) == 0) currentStatus.a0 else Status.SingleTrue,
        src = SourceOfNZ.A)
    },
    ALR -> {(nn, currentStatus) =>
      val n = nn & 0xff
      val newA = currentStatus.a.map(i => (i & n & 0xff) >> 1)
      currentStatus.copy(
        n = newA.n(),
        z = newA.z(),
        c = currentStatus.a.map(i => (i & n & 1) == 0),
        a = newA,
        a0 = newA.bit0,
        a7 = newA.bit7,
        src = SourceOfNZ.A)
   
    },
    ADC_W -> {(nn, currentStatus) =>
      val n = nn & 0xffff
      val newA = currentStatus.aw.adc_w(n, currentStatus.c, currentStatus.d)
      currentStatus.copy(
        n = newA.nw(),
        z = newA.zw(),
        a = newA.lo,
        ah = newA.hi,
        a0 = newA.bit0,
        a7 = newA.bit7,
        c = AnyStatus,
        v = AnyStatus,
        src = SourceOfNZ.AW)
    },
    AND_W -> {(nn, currentStatus) =>
      val n = nn & 0xffff
      val newA = currentStatus.aw.map(_ & 0xffff)
      currentStatus.copy(
        n = newA.nw(),
        z = newA.zw(),
        a = newA.lo | currentStatus.a.map(_ & n & 0xff),
        ah = newA.hi | currentStatus.ah.map(_ & (n >> 8) & 0xff),
        a0 = newA.bit0,
        a7 = newA.bit7,
        src = SourceOfNZ.AW)
    },
    EOR_W -> {(nn, currentStatus) =>
      val n = nn & 0xffff
      val newA = currentStatus.aw.map(_ ^ 0xffff)
      currentStatus.copy(
        n = newA.nw(),
        z = newA.zw(),
        a = newA.lo | currentStatus.a.map(_ ^ (n & 0xff)),
        ah = newA.hi | currentStatus.ah.map(_ ^ ((n >> 8) & 0xff)),
        a0 = newA.bit0,
        a7 = newA.bit7,
        src = SourceOfNZ.AW)
    },
    ORA_W -> {(nn, currentStatus) =>
      val n = nn & 0xffff
      val newA = currentStatus.aw.map(_ | 0xffff)
      currentStatus.copy(
        n = newA.nw(),
        z = newA.zw(),
        a = newA.lo | currentStatus.a.map(_ | (n & 0xff)),
        ah = newA.hi | currentStatus.ah.map(_ | ((n >> 8) & 0xff)),
        a0 = newA.bit0,
        a7 = newA.bit7,
        src = SourceOfNZ.AW)
    },
    CMP -> {(nn, currentStatus) =>
      currentStatus.copy(
        c = if (nn == 0) Status.SingleTrue else currentStatus.a.map(v => (v & 0xff) >= (nn & 0xff)),
        n = currentStatus.a.map(v => v.-(nn).&(0x80).!=(0)),
        z = currentStatus.a.map(v => (v & 0xff) == (nn & 0xff)) |
          currentStatus.a0.flatMap(v => if (v) {
            if ((nn & 1) == 0) Status.SingleFalse
            else AnyStatus
          } else {
            if ((nn & 1) == 0) AnyStatus
            else Status.SingleFalse
          }) |
          currentStatus.a7.flatMap(v => if (v) {
            if ((nn & 0x80) == 0) Status.SingleFalse
            else AnyStatus
          } else {
            if ((nn & 0x80) == 0) AnyStatus
            else Status.SingleFalse
          }),
        src = if (nn == 0) SourceOfNZ.A else AnyStatus)
    },
    CPX -> { (nn, currentStatus) =>
      currentStatus.copy(
        c = if (nn == 0) Status.SingleTrue else currentStatus.x.map(v => (v & 0xff) >= (nn & 0xff)),
        n = currentStatus.x.map(v => v.-(nn).&(0x80).!=(0)),
        z = currentStatus.x.map(v => (v & 0xff) == (nn & 0xff)),
        src = if (nn == 0) SourceOfNZ.X else AnyStatus)
    },
    CPY -> {(nn, currentStatus) =>
      currentStatus.copy(
        c = if (nn == 0) Status.SingleTrue else currentStatus.y.map(v => (v & 0xff) >= (nn & 0xff)),
        n = currentStatus.y.map(v => v.-(nn).&(0x80).!=(0)),
        z = currentStatus.y.map(v => (v & 0xff) == (nn & 0xff)),
        src = if (nn == 0) SourceOfNZ.Y else AnyStatus)
    },
    CPZ -> {(nn, currentStatus) =>
      currentStatus.copy(
        c = if (nn == 0) Status.SingleTrue else currentStatus.iz.map(v => (v & 0xff) >= (nn & 0xff)),
        n = currentStatus.iz.map(v => v.-(nn).&(0x80).!=(0)),
        z = currentStatus.iz.map(v => (v & 0xff) == (nn & 0xff)),
        src = if (nn == 0) SourceOfNZ.Z else AnyStatus)
    },

    SBX -> { (nn, currentStatus) =>
      val newX = (currentStatus.x <*> currentStatus.a) { (x, a) => (x - (x & a)) & 0xff }
      currentStatus.copy(
        x = newX,
        n = newX.n(),
        z = newX.z(),
        c = AnyStatus,
        eqSX = false,
        eqSpX = false,
        src = SourceOfNZ.X)
    },

    TAM -> { (nn, currentStatus) => currentStatus },
    TMA -> { (nn, currentStatus) =>
      val newX = (currentStatus.x <*> currentStatus.a) { (x, a) => (x - (x & a)) & 0xff }
      currentStatus.copy(a = AnyStatus)
    },
  )

  def hasDefinition(opcode: Opcode.Value): Boolean = map.contains(opcode)

  def get(opcode: Opcode.Value): (Int, CpuStatus) => CpuStatus = map(opcode)
}
