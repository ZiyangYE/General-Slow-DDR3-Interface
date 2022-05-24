package ddr3

import spinal.core._
import spinal.lib._

import scala.language.postfixOps

case class slowDDR3Cfg(
                        // Timing config
                        //t means time, counted in ps
                        tRTP: Int = 7500,
                        tRP: Int = 12500,
                        tWTR: Int = 7500,
                        tRC: Int = 55000,
                        tRAS: Int = 37500,
                        tRCD: Int = 12500,
                        tFAW: Int = 40000,
                        tRRD: Int = 7500,
                        tCKE: Int = 75000,
                        tREFI: Int = 7800000,
                        tRFC: Int = 160000,

                        //n means clk count
                        // for it's a slow DDR3, CAS is fixed 6
                        nCAS: Int = 6,
                        nDLLK: Int = 512,

                        // Interface Config
                        dqWidth: Int = 16,
                        // bank Width is fixed 3
                        rowWidth: Int = 14,
                        colWidth: Int = 10,

                        // Clock Frequency, counted in MHz
                        clkFreq: Int = 100
                      ){}

case class DDR3Interface(cfg:slowDDR3Cfg=slowDDR3Cfg()) extends Bundle{
  val address: UInt = out UInt(cfg.rowWidth bits)
  val bank: UInt = out UInt(3 bits)
  val cs: Bool = out Bool()
  val cas: Bool = out Bool()
  val ras: Bool = out Bool()
  val we: Bool = out Bool()
  val clk_p: Bool = out Bool()
  val clk_n: Bool = out Bool()
  val cke: Bool = out Bool()
  val odt: Bool = out Bool()
  val rst_n: Bool = out Bool()
  val dm: UInt = out UInt(cfg.dqWidth/8 bits)
  val dq: UInt = inout(Analog( UInt(cfg.dqWidth bits)))
  val dqs_p: UInt = inout(Analog(UInt(cfg.dqWidth/8 bits)))
  val dqs_n: UInt = inout(Analog(UInt(cfg.dqWidth/8 bits)))
}

case class DDR3SystemIO(cfg:slowDDR3Cfg=slowDDR3Cfg()) extends Bundle with IMasterSlave{
  val dataRd: Stream[UInt] = Stream(UInt(cfg.dqWidth bits))
  val dataWr: Stream[UInt] = Stream(UInt(cfg.dqWidth bits))
  val initFin: Bool = Bool()

  override def asMaster(): Unit = {
    master(dataWr)
    slave(dataRd)
    in(initFin)
  }

  override def asSlave(): Unit = {
    slave(dataWr)
    master(dataRd)
    out(initFin)
  }
}


class slowDDR3(cfg:slowDDR3Cfg=slowDDR3Cfg()) extends Component {
  val inv_clk = in Bool()//input a inverted clock for the dqs

  val phyIO = DDR3Interface(cfg)
  val sysIO = slave(DDR3SystemIO(cfg))

  //const value
  phyIO.cs := False
  phyIO.odt := False

  //init value
  phyIO.address.setAsReg() init UInt(cfg.rowWidth bits).setAll()
  phyIO.bank.setAsReg() init 0
  phyIO.cas.setAsReg() init True
  phyIO.ras.setAsReg() init True
  phyIO.we.setAsReg() init True
  phyIO.clk_p.setAsReg() init False
  phyIO.clk_n.setAsReg() init True
  phyIO.cke.setAsReg() init False
  phyIO.rst_n.setAsReg() init False
  phyIO.dm.setAsReg() init 0


  //dq and dqs is tri-state
  val dqReg = Reg(UInt(cfg.dqWidth bits)) init 0
  val dqsWire = UInt(cfg.dqWidth/8 bits)
  val dqOut = RegInit(False)

  when(dqOut){
    phyIO.dq := dqReg
    phyIO.dqs_p := dqsWire
    phyIO.dqs_n := ~dqsWire
  }

  sysIO.dataRd.payload.setAsReg() init 0
  sysIO.dataRd.valid.setAsReg() init False
  sysIO.dataWr.ready.setAsReg() init False
  sysIO.initFin.setAsReg() init False


  //state
  object InitState extends SpinalEnum{
    val WAIT0, CKE, MRS2, MRS3, MRS1, MRS0, ZQCL, WAIT1 = newElement()
  }
  import InitState._

  val initState = RegInit(WAIT0)
  val initFin = sysIO.initFin


  //internal counter
  val timer = new Area{
    val counter = Reg(UInt(24 bits)) init(200*cfg.clkFreq) // wait for 200us after reset

    counter := counter - 1
    val tick = counter === 0
  }

  //ddr clock generator
  val clkGen = new Area{
    val clk = Reg(Bool) init False

    clk := ~clk

    phyIO.clk_p := clk
    phyIO.clk_n := ~clk
  }

  val userReset = Reg(Bool) init False
  userReset := True

  //dqs domain
  val dqsDomain = ClockDomain(inv_clk,userReset)
  val dqsGen = new ClockingArea(dqsDomain){
    val clk = Reg(Bool) init False

    clk := ~clk

    val dqsReg = Reg(UInt(cfg.dqWidth/8 bits)) init 0
    dqsReg.setAllTo(clk)
  }
  dqsWire := dqsGen.dqsReg


  val StateMachine = new Area {
    phyIO.cas := True
    phyIO.ras := True
    phyIO.we := True

    when(initFin) {
      // normal operation

    } otherwise {
      // init
      switch(initState) {
        is(WAIT0) {
          when(timer.tick) {
            phyIO.rst_n := True

            timer.counter := 500*cfg.clkFreq // wait for 500us
            initState := CKE
          }
        }
        is(CKE) {
          when(timer.tick) {
            phyIO.cke := True

            if((cfg.tRFC+10000)*cfg.clkFreq/1000000 > 5) {
              timer.counter := (cfg.tRFC+10000)*cfg.clkFreq/1000000
            } else {
              timer.counter := 5
            }
            initState := MRS2
          }
        }
        is(MRS2) { // set MRS2 to 0
          when(timer.tick){
            phyIO.cas := False
            phyIO.ras := False
            phyIO.we := False

            phyIO.address := 0
            phyIO.bank := U"010"

            timer.counter := 4
            initState := MRS3
          }
        }
        is(MRS3) { // set MRS3 to 0
          when(timer.tick){
            phyIO.cas := False
            phyIO.ras := False
            phyIO.we := False

            phyIO.address := 0
            phyIO.bank := U"011"

            timer.counter := 4
            initState := MRS1
          }
        }
        is(MRS1) { //set MRS1 to 0044
          when(timer.tick){
            phyIO.cas := False
            phyIO.ras := False
            phyIO.we := False

            phyIO.address := 0x44
            phyIO.bank := U"001"

            timer.counter := 4
            initState := MRS0
          }
        }
        is(MRS0) { // set MRS0 to 0320
          when(timer.tick){
            phyIO.cas := False
            phyIO.ras := False
            phyIO.we := False

            phyIO.address := 0x320
            phyIO.bank := U"000"

            timer.counter := 12
            initState := ZQCL
          }
        }
        is(ZQCL) {
          when(timer.tick){
            phyIO.we := False

            phyIO.address := 0x400

            timer.counter := 512
            initState := WAIT1
          }
        }
        is(WAIT1) {
          when(timer.tick){
            initFin := True

            timer.counter := cfg.tREFI*cfg.clkFreq / 1000000
          }
        }
      }
    }
  }


}

object SDDR3GenCfg extends SpinalConfig(
  defaultConfigForClockDomains = ClockDomainConfig(
    resetKind = ASYNC,
    resetActiveLevel = LOW
  )
)

object DDR3Generate {
  def main(args: Array[String]) {
    SDDR3GenCfg.generateVerilog(new slowDDR3(slowDDR3Cfg()))
  }
}