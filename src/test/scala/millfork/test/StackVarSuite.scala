package millfork.test

import millfork.Cpu
import millfork.test.emu.{EmuCrossPlatformBenchmarkRun, EmuOptimizedZ80Run, EmuSoftwareStackBenchmarkRun, EmuUnoptimizedCrossPlatformRun}
import org.scalatest.{FunSuite, Matchers}

/**
  * @author Karol Stasiak
  */
class StackVarSuite extends FunSuite with Matchers {

  test("Basic stack assignment") {
    EmuCrossPlatformBenchmarkRun(Cpu.StrictMos, Cpu.Z80, Cpu.Intel8080, Cpu.Intel8085, Cpu.Sharp, Cpu.Intel8086, Cpu.Motorola6809)("""
        | byte output @$c000
        | void main () {
        |   stack byte a, b
        |   b = 4
        |   a = b
        |   output = a
        |   a = output
        | }
      """.stripMargin)(_.readByte(0xc000) should equal(4))
  }

  test("Stack byte addition") {
    EmuCrossPlatformBenchmarkRun(Cpu.StrictMos, Cpu.Z80, Cpu.Intel8080, Cpu.Sharp, Cpu.Intel8086, Cpu.Motorola6809)("""
        | byte output @$c000
        | void main () {
        |   stack byte a, b
        |   a = $11
        |   b = $44
        |   b += zzz()
        |   b += a
        |   output = b
        | }
        | byte zzz() {
        |   return $22
        | }
      """.stripMargin)(_.readByte(0xc000) should equal(0x77))
  }

  test("Complex expressions involving stack variables (6502)") {
    EmuSoftwareStackBenchmarkRun("""
        | byte output @$c000
        | void main () {
        |   stack byte a
        |   a = 7
        |   output = f(a) + f(a) + f(a)
        | }
        | asm byte f(byte a) {
        |   rts
        | }
      """.stripMargin)(_.readWord(0xc000) should equal(21))
  }

  test("Complex expressions involving stack variables (Z80)") {
    EmuCrossPlatformBenchmarkRun(Cpu.Z80, Cpu.Intel8080, Cpu.Sharp, Cpu.Intel8086)("""
        | byte output @$c000
        | void main () {
        |   stack byte a
        |   a = 7
        |   output = f(a) + f(a) + f(a)
        | }
        | asm byte f(byte a) {
        |   ret
        | }
      """.stripMargin)(_.readWord(0xc000) should equal(21))
  }

  test("Stack byte subtraction") {
    EmuUnoptimizedCrossPlatformRun(Cpu.Mos, Cpu.Z80, Cpu.Intel8080, Cpu.Intel8086, Cpu.Motorola6809)(
      """
        | byte output @$c000
        | void main () {
        |   stack byte a, b
        |   b = $77
        |   a = $11
        |   b -= zzz()
        |   b -= a
        |   output = b
        | }
        | byte zzz() {
        |   return $22
        | }
      """.stripMargin) { m =>
      m.readByte(0xc000) should equal(0x44)
    }
  }

  test("Stack word addition") {
    EmuCrossPlatformBenchmarkRun(Cpu.StrictMos, Cpu.Z80, Cpu.Intel8080, Cpu.Intel8085, Cpu.Sharp, Cpu.Intel8086, Cpu.Motorola6809)("""
        | word output @$c000
        | void main () {
        |   stack word a, b
        |   a = $111
        |   b = $444
        |   b += zzz()
        |   b += a
        |   output = b
        | }
        | word zzz() {
        |   return $222
        | }
      """.stripMargin)(_.readWord(0xc000) should equal(0x777))
  }

  test("Recursion") {
    EmuCrossPlatformBenchmarkRun(Cpu.StrictMos, Cpu.Z80, Cpu.Intel8080, Cpu.Intel8085, Cpu.Sharp, Cpu.Intel8086, Cpu.Motorola6809)("""
        | array output [6] @$c000
        | byte fails @$c010
        | void main () {
        |   word w
        |   byte i
        |   for i,0,until,output.length {
        |     w = fib(i)
        |     if w.hi != 0 { fails += 1 }
        |     output[i] = w.lo
        |   }
        | }
        | word fib(byte i) {
        |   stack byte j
        |   stack word sum, w, v
        |   j = i
        |   if j < 2 {
        |     return 1
        |   }
        |
        |   sum = fib(j-1)
        |
        |   // see if optimizer cleans these off:
        |   w = sum
        |   sum = w
        |   v = sum
        |   sum = v
        |
        |   sum += fib(j-2)
        |   sum &= $0F3F
        |   return sum
        | }
      """.stripMargin){ m =>
      m.readByte(0xc010) should equal(0)
      m.readByte(0xc000) should equal(1)
      m.readByte(0xc001) should equal(1)
      m.readByte(0xc002) should equal(2)
      m.readByte(0xc003) should equal(3)
      m.readByte(0xc004) should equal(5)
      m.readByte(0xc005) should equal(8)
    }
  }

  test("Recursion 2") {
    EmuCrossPlatformBenchmarkRun(Cpu.StrictMos, Cpu.Z80, Cpu.Intel8080, Cpu.Sharp, Cpu.Intel8086, Cpu.Motorola6809)("""
        | array output [6] @$c000
        | byte fails @$c010
        | void main () {
        |   word w
        |   byte i
        |   for i,0,until,output.length {
        |     w = fib(i)
        |     if w.hi != 0 { fails += 1 }
        |     output[i] = w.lo
        |   }
        | }
        | word fib(byte i) {
        |   stack byte j
        |   j = i
        |   if j < 2 {
        |     return 1
        |   }
        |   return fib(j-1) + fib(j-2)
        | }
      """.stripMargin){ m =>
      m.readByte(0xc010) should equal(0)
      m.readByte(0xc000) should equal(1)
      m.readByte(0xc001) should equal(1)
      m.readByte(0xc002) should equal(2)
      m.readByte(0xc003) should equal(3)
      m.readByte(0xc004) should equal(5)
      m.readByte(0xc005) should equal(8)
    }
  }

  test("Complex stack-related stuff") {
    EmuCrossPlatformBenchmarkRun(Cpu.StrictMos, Cpu.Z80, Cpu.Intel8080, Cpu.Sharp, Cpu.Intel8086, Cpu.Motorola6809)("""
        | byte output @$c000
        | array id = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
        | void main() {
        |   stack byte i
        |   i = b($4)
        |   output = b(id[b($2)]|id[b(i)])
        | }
        | noinline byte b(byte a) {
        |   return a
        | }
      """.stripMargin){ m =>
      m.readByte(0xc000) should equal(6)
    }
  }


  test("Indexing") {
    EmuCrossPlatformBenchmarkRun(Cpu.StrictMos, Cpu.Z80, Cpu.Intel8080, Cpu.Sharp, Cpu.Intel8086, Cpu.Motorola6809)("""
        | array output [200] @$c000
        | void main () {
        |   stack byte a, b
        |   a = $11
        |   b = $44
        |   output[a + b] = $66
        | }
      """.stripMargin){m => m.readByte(0xc055) should equal(0x66) }
  }

    test("Double array with stack variables") {
      EmuCrossPlatformBenchmarkRun(Cpu.StrictMos, Cpu.Z80, Cpu.Intel8080, Cpu.Sharp, Cpu.Intel8086, Cpu.Motorola6809)(
        """
          | array output[5]@$c001
          | array input = [0,1,4,9,16,25,36,49]
          | void main () {
          |   stack byte i
          |   for i,0,until,output.length {
          |     output[i] = input[i+1]<<1
          |   }
          | }
          | void _panic(){while(true){}}
        """.stripMargin){ m=>
        m.readByte(0xc001) should equal (2)
        m.readByte(0xc005) should equal (50)
      }
    }

    test("Complex large stacks") {
      EmuCrossPlatformBenchmarkRun(Cpu.StrictMos, Cpu.Z80, Cpu.Intel8080, Cpu.Sharp, Cpu.Intel8086, Cpu.Motorola6809)(
//      val m = EmuUnoptimizedZ80Run(
        """
          | array output[5]@$c000
          | noinline byte f(byte y) {
          |   stack int56 param
          |   param = y
          |   param -= 1
          |   if param == 0 {
          |     return 1
          |   }
          |   return f(param.b0) + 1
          | }
          | void main () {
          |   output[0] = f(7)
          |   output[1] = f(8)
          |   output[2] = f(9)
          |   output[3] = f(2)
          | }
          | void _panic(){while(true){}}
        """.stripMargin) { m =>
        m.readByte(0xc000) should equal(7)
        m.readByte(0xc001) should equal(8)
        m.readByte(0xc002) should equal(9)
        m.readByte(0xc003) should equal(2)
      }
    }

  test("Large stack storage") {
    EmuUnoptimizedCrossPlatformRun(Cpu.Mos, Cpu.Z80, Cpu.Intel8080, Cpu.Sharp, Cpu.Intel8086, Cpu.Motorola6809)(
      """
        | int32 output @$c000
        | void main () {
        |   stack int32 a
        |   a = f()
        |   barrier()
        |   output = a
        | }
        | noinline int32 f() = 400
        | noinline void barrier(){}
      """.stripMargin) { m =>
      m.readLong(0xc000) should equal(400)
    }
  }

  test("Large stack-to-stack transfer") {
    EmuCrossPlatformBenchmarkRun(Cpu.Mos, Cpu.Z80, Cpu.Intel8080, Cpu.Sharp, Cpu.Intel8086, Cpu.Motorola6809)(
      """
        | int32 output @$c000
        | void main () {
        |   stack int32 a, b
        |   a = f()
        |   barrier()
        |   b = a
        |   barrier()
        |   output = b
        | }
        | noinline int32 f() = 400
        | noinline void barrier(){}
      """.stripMargin) { m =>
      m.readLong(0xc000) should equal(400)
    }
  }

  test("Pointers to stack variables 1") {
    val code =
      """
        | noinline void inc(pointer.byte p) {
        |   p[0] += 1
        |   test1 = word(p)
        | }
        | byte output @$c000
        | word test1 @$c002
        | word stkptr @$c004
        | void main () {
        |   stack byte a
        |   a = 0
        |   inc(a.pointer) // ↑
        |   inc(a.pointer) // ↑
        |   inc(a.pointer) // ↑
        |   inc(a.pointer) // ↑
        |   inc(a.pointer) // ↑
        |   inc(a.pointer) // ↑
        |   inc(a.pointer) // ↑
        |   inc(a.pointer) // ↑
        |   inc(a.pointer) // ↑
        |   output = a
        |   #if ARCH_6502
        |   asm {
        |     tsx
        |     stx stkptr
        |     ldx #0
        |     stx stkptr+1
        |   }
        |   #endif
        |   #if ARCH_I80 && CPUFEATURE_8080
        |   #pragma zilog_syntax
        |   asm {
        |     ld hl,0
        |     add hl,sp
        |     ld (stkptr),hl
        |   }
        |   #endif
        |   #if CPUFEATURE_GAMEBOY
        |   #pragma zilog_syntax
        |   asm {
        |     ld hl,0
        |     add hl,sp
        |     ld a,l
        |     ld (stkptr),a
        |     ld a,h
        |     ld (stkptr+1),a
        |   }
        |   #endif
        | }
      """.stripMargin

    EmuCrossPlatformBenchmarkRun(Cpu.Mos, Cpu.Cmos, Cpu.Z80, Cpu.Intel8080, Cpu.Sharp, Cpu.Motorola6809)(code) { m =>
      println("$" + m.readWord(0xc002).toHexString)
//      (0x1f0 until 0x200).foreach { a =>
//        printf(f"$$$a%04x = $$${m.readByte(a)}%02x%n");
//      }
//      (0xc000 until 0xc010).foreach { a =>
//        printf(f"$$$a%04x = $$${m.readByte(a)}%02x%n");
//      }
      println("$" + m.readWord(0xc004).toHexString)
      m.readByte(0xc000) should equal(code.count(_ == '↑'))
    }
    EmuSoftwareStackBenchmarkRun(code) { m =>
      println("$" + m.readWord(0xc002).toHexString)
      println("$" + m.readWord(0xc004).toHexString)
      m.readByte(0xc000) should equal(code.count(_ == '↑'))
    }
  }


  test("Pointers to stack variables 2") {
    val code =
      """
        | import zp_reg
        | void main () {
        |   stack byte a
        |   a = 0
        |   byte b
        |   word w
        |   b += a.addr.lo
        |   w += a.addr
        |   w <<= a.addr.hi
        |   b *= a.addr.lo
        |   b <<= a.addr.lo
        |   w = nonet(b << a.addr.lo)
        |   w &= a.addr
        |   w = w | a.addr
        |
        | }
      """.stripMargin

    EmuUnoptimizedCrossPlatformRun(Cpu.Mos, Cpu.Z80, Cpu.Motorola6809)(code) { m =>
    }
    EmuSoftwareStackBenchmarkRun(code) { m =>
    }
  }

  test("Pointers to stack variables 3") {
    val m = EmuOptimizedZ80Run(
      """
        | noinline byte f() = 6
        | noinline void g(byte x) {}
        | void main () {
        |   stack byte b, a, c
        |      b = b
        |      c = c
        |   a = f()
        |   a += 5
        |   a += a.addr.lo
        |   g(a)
        |   if f() == 0 { main() }
        | }
      """.stripMargin)
  }

  test("Pointers to stack variables 4") {
    val m = EmuOptimizedZ80Run(
      """
        | noinline word f() = 6
        | noinline void g(word x) {}
        | asm void __loop() {
        |   nop
        |   nop
        |   nop
        |   jp __loop
        | }
        | noinline void h (pointer.word p) {
        |   p[0] = __loop.addr + 1
        | }
        | void main () {
        |   stack word b, a, c, d
        |   h(a.pointer)
        |   h(b.pointer)
        |   h(c.pointer)
        |   h(d.pointer)
        |   a = f()
        |   a += 5
        |   a += a.addr.lo
        |   g(a)
        |   if f() == 0 { main() }
        | }
      """.stripMargin)
  }




}