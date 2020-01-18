package millfork.test.emu

import millfork.assembly.mos.opt.{LaterOptimizations, UndocumentedOptimizations}
import millfork.{Cpu, OptimizationPresets}

/**
  * @author Karol Stasiak
  */
object EmuUndocumentedRun extends EmuRun(
  Cpu.Ricoh, // not Cpu.Mos, because I haven't found an emulator that supports both illegals and decimal mode yet
  OptimizationPresets.NodeOpt,
  OptimizationPresets.AssOpt ++ LaterOptimizations.Nmos ++
    UndocumentedOptimizations.All ++
    OptimizationPresets.Good ++ LaterOptimizations.Nmos ++
    UndocumentedOptimizations.All ++
    OptimizationPresets.AssOpt ++ LaterOptimizations.Nmos ++
    UndocumentedOptimizations.All ++
    OptimizationPresets.AssOpt ++ LaterOptimizations.Nmos ++
    UndocumentedOptimizations.All ++
    OptimizationPresets.Good) {

  override def emitIllegals = true
}



