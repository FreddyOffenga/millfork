package millfork.test

import millfork.Cpu
import millfork.test.emu.{EmuBenchmarkRun, EmuCrossPlatformBenchmarkRun}
import org.scalatest.{FunSuite, Matchers}

/**
  * @author Karol Stasiak
  */
class FarwordTest extends FunSuite with Matchers {

  test("Int24 assignment") {
    EmuCrossPlatformBenchmarkRun(Cpu.Mos, Cpu.Z80, Cpu.Intel8080, Cpu.Sharp, Cpu.Intel8086, Cpu.Motorola6809)(
      """
        | int24 output3 @$c000
        | int24 output2 @$c004
        | int24 output1 @$c008
        | void main () {
        |   output3 = $223344
        |   output2 = $223344
        |   output1 = $223344
        |   output2 = $7788
        |   output1 = $55
        | }
      """.stripMargin) { m =>
      m.readMedium(0xc000) should equal(0x223344)
      m.readMedium(0xc004) should equal(0x7788)
      m.readMedium(0xc008) should equal(0x55)
    }
  }
  test("Int24 assignment 2") {
    EmuCrossPlatformBenchmarkRun(Cpu.Mos, Cpu.Z80, Cpu.Intel8080, Cpu.Sharp, Cpu.Intel8086, Cpu.Motorola6809)(
      """
        | int24 output3 @$c000
        | int24 output2 @$c004
        | word output1 @$c008
        | void main () {
        |   word w
        |   byte b
        |   w = $7788
        |   b = $55
        |   output3 = $23344
        |   output2 = $23344
        |   output1 = $3344
        |   output2 = w
        |   output1 = b
        | }
      """.stripMargin) { m =>
      m.readMedium(0xc000) should equal(0x23344)
      m.readMedium(0xc004) should equal(0x7788)
      m.readWord(0xc008) should equal(0x55)
    }
  }

  test("Int24 assignment 3") {
    EmuCrossPlatformBenchmarkRun(Cpu.Mos, Cpu.Z80, Cpu.Intel8080, Cpu.Sharp, Cpu.Intel8086, Cpu.Motorola6809)(
      """
        | int24 output0 @$c000
        | int24 output1 @$c003
        | void main () {
        |   output0 = $112233
        |   output1 = $112233
        |   output0.hiword = output0.loword
        |   output1.loword = output1.hiword
        | }
      """.stripMargin) { m =>
      // TODO: this fails right now:
      m.readMedium(0xc000) should equal(0x223333)
      m.readMedium(0xc003) should equal(0x111122)
    }
  }
  test("Int24 addition") {
    EmuCrossPlatformBenchmarkRun(Cpu.Mos, Cpu.Z80, Cpu.Intel8080, Cpu.Sharp, Cpu.Intel8086, Cpu.Motorola6809)(
      """
        | int24 output @$c000
        | void main () {
        |   word w
        |   int24 l
        |   byte b
        |   w = $8000
        |   b = $8
        |   l = $50000
        |   output = 0
        |   output += l
        |   output += w
        |   output += b
        | }
      """.stripMargin) { m =>
      m.readMedium(0xc000) should equal(0x58008)
    }
  }
  test("Int24 addition 2") {
    EmuCrossPlatformBenchmarkRun(Cpu.Mos, Cpu.Z80, Cpu.Intel8080, Cpu.Sharp, Cpu.Intel8086, Cpu.Motorola6809)(
      """
        | int24 output @$c000
        | void main () {
        |   output = 0
        |   output += $50000
        |   output += $8000
        |   output += $8
        | }
      """.stripMargin) { m =>
      m.readMedium(0xc000) should equal(0x58008)
    }
  }
  test("Int24 subtraction") {
    EmuCrossPlatformBenchmarkRun(Cpu.Mos, Cpu.Z80, Cpu.Intel8080, Cpu.Sharp, Cpu.Intel8086, Cpu.Motorola6809)(
      """
        | int24 output @$c000
        | void main () {
        |   word w
        |   int24 l
        |   byte b
        |   w = $8000
        |   b = $8
        |   l = $50000
        |   output = $58008
        |   output -= l
        |   output -= w
        |   output -= b
        | }
      """.stripMargin) { m =>
      m.readMedium(0xc000) should equal(0)
    }
  }
  test("Int24 subtraction 2") {
    EmuCrossPlatformBenchmarkRun(Cpu.Mos, Cpu.Z80, Cpu.Intel8080, Cpu.Sharp, Cpu.Intel8086, Cpu.Motorola6809)(
      """
        | int24 output @$c000
        | void main () {
        |   output = $58008
        |   output -= $50000
        |   output -= $8000
        |   output -= $8
        | }
      """.stripMargin) { m =>
      m.readMedium(0xc000) should equal(0)
    }
  }
  test("Int24 subtraction 3") {
    EmuCrossPlatformBenchmarkRun(Cpu.Mos, Cpu.Z80, Cpu.Intel8080, Cpu.Sharp, Cpu.Intel8086, Cpu.Motorola6809)(
      """
        | int24 output @$c000
        | void main () {
        |   output = $58008
        |   output -= w()
        |   output -= b()
        | }
        | byte b() {
        |   return $8
        | }
        | word w() {
        |   return $8000
        | }
      """.stripMargin) { m =>
      m.readMedium(0xc000) should equal(0x50000)
    }
  }

  test("Int24 AND") {
    EmuCrossPlatformBenchmarkRun(Cpu.Mos, Cpu.Z80, Cpu.Intel8080, Cpu.Sharp, Cpu.Intel8086, Cpu.Motorola6809)(
      """
        | int24 output @$c000
        | void main () {
        |   output = $FFFFFF
        |   output &= w()
        |   output &= b()
        | }
        | byte b() {
        |   return $77
        | }
        | word w() {
        |   return $CCCC
        | }
      """.stripMargin) { m =>
      m.readMedium(0xc000) should equal(0x44)
    }
  }

  test("Int24 INC/DEC") {
    EmuCrossPlatformBenchmarkRun(Cpu.Mos, Cpu.Z80, Cpu.Intel8080, Cpu.Sharp, Cpu.Intel8086, Cpu.Motorola6809)(
      """
        | int24 output0 @$c000
        | int24 output1 @$c004
        | int24 output2 @$c008
        | int24 output3 @$c00c
        | int24 output4 @$c010
        | int24 output5 @$c014
        | int24 output6 @$c018
        | void main () {
        |   output0 = 0
        |   output1 = $FF
        |   output2 = $FFFF
        |   output3 = $FF00
        |   output4 = $FF00
        |   output5 = $10000
        |   output6 = 0
        |   barrier()
        |   output0 += 1
        |   output1 += 1
        |   output2 += 1
        |   output3 += 1
        |   output4 -= 1
        |   output5 -= 1
        |   output6 -= 1
        | }
        | void barrier() {
        | }
      """.stripMargin) { m =>
      m.readMedium(0xc000) should equal(1)
      m.readMedium(0xc004) should equal(0x100)
      m.readMedium(0xc008) should equal(0x10000)
      m.readMedium(0xc00c) should equal(0xff01)
      m.readMedium(0xc010) should equal(0xfeff)
      m.readMedium(0xc014) should equal(0xffff)
      m.readMedium(0xc018) should equal(0xffffff)
    }
  }
}
