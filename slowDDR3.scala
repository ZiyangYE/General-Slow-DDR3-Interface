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
  nZQInit: Int = 512,

  // Interface Config
  dqWidth: Int = 16,
  // bank Width is fixed 3
  rowWidth: Int = 14,
  colWidth: Int = 10,

  // DDR Clock Frequency, counted in MHz
  // It is half to the sys clk
  clkFreq: Int = 50,

  // use multiple counters could increase the frequency, but the size would be larger
  // TBD.
  multipleCounters: Boolean = false,

  // use burst could significantly increase the r/w rate to about 4 times, but the size would also be increased.
  // TBD.
  enableBursts: Boolean = false
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
  val address: UInt = UInt(3+cfg.rowWidth+cfg.colWidth bits)
  val initFin: Bool = Bool()

  override def asMaster(): Unit = {
    master(dataWr)
    slave(dataRd)
    in(initFin)
    out(address)
  }

  override def asSlave(): Unit = {
    slave(dataWr)
    master(dataRd)
    out(initFin)
    in(address)
  }
}


class slowDDR3(cfg:slowDDR3Cfg=slowDDR3Cfg()) extends Component {
  def calcCK(t:Int) = t * cfg.clkFreq / 1000000


  val inv_clk = in Bool()//input a inverted clock for the dqs

  val phyIO = DDR3Interface(cfg)
  val sysIO = slave(DDR3SystemIO(cfg))

  //const value
  phyIO.cs := False
  phyIO.odt := False
  phyIO.dm := 0

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

  //dq and dqs is tri-state
  val dqWire = UInt(cfg.dqWidth bits)
  val dqsPWire = UInt(cfg.dqWidth/8 bits)
  val dqsNWire = UInt(cfg.dqWidth/8 bits)
  val dqOut = RegInit(False)

  when(dqOut){
    phyIO.dq := dqWire
    phyIO.dqs_p := dqsPWire
    phyIO.dqs_n := dqsNWire
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

  object WorkState extends SpinalEnum{
    val IDLE, READ, WRITE, REFRESH = newElement()
  }
  import WorkState._

  val initState = RegInit(WAIT0)
  val initFin = sysIO.initFin

  val workState = RegInit(IDLE)
  val refreshCount = RegInit(U"0000")
  val refreshIssued = RegInit(False)

  //ddr clock generator
  val clkGen = new Area{
    val clk = Reg(Bool) init False

    clk := ~clk

    phyIO.clk_p := clk
    phyIO.clk_n := ~clk
  }

  //internal counter
  val timer = new Area{
    val counter = Reg(UInt(24 bits)) init(200*cfg.clkFreq) // wait for 200us after reset

    when(clkGen.clk === False){counter := counter - 1}

    val tick = RegNext(counter === 0)

    val wkCnt = Reg(UInt(8 bits)) init 0

    when(clkGen.clk === False){wkCnt := wkCnt - 1}
  }

  val dqsOut = RegInit(False)
  val dqIn = RegInit(False)
  val IOArea = new Area{
    val clk = Reg(Bool) init False
    val cnt = RegInit(U"00000")

    when(dqsOut === False){
      clk := True
    }otherwise{
      clk := ~clk
    }

    when(dqsOut === False && dqIn === False){
      cnt := 0
    }otherwise{
      cnt := cnt + 1
    }

    when(dqsOut === True) {
      when(cnt === 1) {
        sysIO.dataWr.ready := True
      }
      when(cnt === 9) {
        sysIO.dataWr.ready := False
      }
    }

    val dqsPReg = Reg(UInt(cfg.dqWidth/8 bits)) init 0
    dqsPReg.setAllTo(clk)
    val dqsNReg = Reg(UInt(cfg.dqWidth/8 bits)) init 0
    dqsNReg.setAllTo(~clk)

    //dq out domain
    val dqOutDomain = ClockDomain(inv_clk)
    val dqOutArea = new ClockingArea(dqOutDomain){
      val dqReg = Reg(UInt(cfg.dqWidth bits))
      dqReg.addTag(crossClockDomain)

      dqReg := sysIO.dataWr.payload
      dqWire := dqReg
    }

    val dqInP = UInt(cfg.dqWidth bits)
    val dqInN = UInt(cfg.dqWidth bits)

    val dqInPDomain = new Array[ClockDomain](cfg.dqWidth/8)
    val dqInNDomain = new Array[ClockDomain](cfg.dqWidth/8)
    val dqInPArea = new Array[ClockingArea](cfg.dqWidth/8)
    val dqInNArea = new Array[ClockingArea](cfg.dqWidth/8)

    for( i <- 0 until cfg.dqWidth/8 ){
      dqInPDomain(i) = ClockDomain(phyIO.dqs_p(i))
      dqInNDomain(i) = ClockDomain(phyIO.dqs_n(i))

      dqInPArea(i) = new ClockingArea(dqInPDomain(i)) {
        val dqReg = RegNext(phyIO.dq(8 * i, 8 bits))
        dqReg.addTag(crossClockDomain)
        dqInP(8 * i, 8 bits) := dqReg
      }
      dqInNArea(i) = new ClockingArea(dqInNDomain(i)) {
        val dqReg = RegNext(phyIO.dq(8 * i, 8 bits))
        dqReg.addTag(crossClockDomain)
        dqInN(8 * i, 8 bits) := dqReg
      }
    }

    val intInValid = RegInit(False)

    when(dqIn === True){
      when(cnt === 0){
        intInValid := True
      }
      when(cnt === 8){
        intInValid := False
      }
    }

    sysIO.dataRd.valid := intInValid
    when(intInValid){
      when(cnt(0)){
        sysIO.dataRd.payload := dqInP
      }otherwise{
        sysIO.dataRd.payload := dqInN
      }
    }

  }
  dqsPWire := IOArea.dqsPReg
  dqsNWire := IOArea.dqsNReg




  val StateMachine = new Area {
    def actREFRESH()={
      phyIO.ras := False
      phyIO.cas := False
    }
    def actACTIVATE() ={
      phyIO.ras := False
      phyIO.bank := sysIO.address(cfg.colWidth, 3 bits)
      phyIO.address := sysIO.address(cfg.colWidth + 3, cfg.rowWidth bits)
    }
    def actWRITE() ={
      //with auto precharge
      phyIO.cas := False
      phyIO.we := False
      phyIO.bank := sysIO.address(cfg.colWidth, 3 bits)
      phyIO.address(10) := True
      if(cfg.colWidth == 11){
        phyIO.address(11) := sysIO.address(10)
        phyIO.address(9 downto 0) := sysIO.address(9 downto 0)
      }else{
        phyIO.address(11) := False
        phyIO.address(9 downto 0) := sysIO.address(9 downto 0)
      }
    }
    def actREAD() ={
      //with auto precharge
      phyIO.cas := False
      phyIO.bank := sysIO.address(cfg.colWidth, 3 bits)
      phyIO.address(10) := True
      if(cfg.colWidth == 11){
        phyIO.address(11) := sysIO.address(10)
        phyIO.address(9 downto 0) := sysIO.address(9 downto 0)
      }else{
        phyIO.address(11) := False
        phyIO.address(9 downto 0) := sysIO.address(9 downto 0)
      }
    }


    when(clkGen.clk === False) {
      phyIO.cas := True
      phyIO.ras := True
      phyIO.we := True

      when(initFin) {
        // normal operation
        when(timer.tick){
          refreshCount := refreshCount + 1
          timer.counter := calcCK(cfg.tREFI)
        }otherwise{
          when(refreshIssued){
            refreshCount := refreshCount - 1
            refreshIssued := False
          }
        }

        switch(workState){
          // idle
          is(IDLE){
            when(sysIO.dataRd.ready === False && sysIO.dataWr.valid === False && refreshCount > 0){
              workState := REFRESH
              timer.wkCnt := calcCK(cfg.tRFC)
            }

            // if read and write are issued at the same time
            // the read command would be ignored
            when(sysIO.dataRd.ready === True && sysIO.dataWr.valid === False){
              workState := READ
              timer.wkCnt := calcCK(cfg.tRCD)+1 + 6 + 4
            }

            when(sysIO.dataWr.valid === True){
              workState := WRITE
              timer.wkCnt := calcCK(cfg.tRCD)+1 + 6 + 4 + calcCK(cfg.tWTR) + calcCK(cfg.tRP) + 1 + 5
            }
          }

          is(REFRESH){
            when(timer.wkCnt === calcCK(cfg.tRFC)){
              // issue the refresh command
              actREFRESH()
              refreshIssued := True
            }

            when(timer.wkCnt === 0) {
              when(
                refreshCount > 8 ||
                  (refreshCount =/= 0 && sysIO.dataWr.ready === False && sysIO.dataRd.valid === False)
              ) {
                // if refreshCount > 8, force another refresh
                // otherwise, it no read or write request, do another refresh
                timer.wkCnt := calcCK(cfg.tRFC)
              } otherwise {
                // go back to idle
                workState := IDLE
              }
            }
          }

          is(READ){
            when(timer.wkCnt === calcCK(cfg.tRCD)+1 + 6 + 4){
              actACTIVATE()
            }
            when(timer.wkCnt === 6 + 4){
              actREAD()
            }
            when(timer.wkCnt === 5){
              dqIn := True
            }
            when(timer.wkCnt === 0){
              dqIn := False

              when(refreshCount > 8){
                workState := REFRESH
                timer.wkCnt := calcCK(cfg.tRFC)
              }otherwise{
                when(sysIO.dataRd.ready){
                  timer.wkCnt := calcCK(cfg.tRCD)+1 + 6 + 4
                }otherwise{
                  workState:=IDLE
                }
              }
            }
          }

          is(WRITE){
            when(timer.wkCnt === calcCK(cfg.tRCD)+1 + 6 + 4 + calcCK(cfg.tWTR) + calcCK(cfg.tRP) + 1 + 5){
              //issue ACT
              actACTIVATE()
            }
            when(timer.wkCnt === 6 + 4 + calcCK(cfg.tWTR) + calcCK(cfg.tRP) + 1 + 5){
              actWRITE()
              dqOut := True
            }
            when(timer.wkCnt === 4 + 1 + calcCK(cfg.tWTR) + calcCK(cfg.tRP) + 1 + 5){
              dqsOut := True
            }
            when(timer.wkCnt === calcCK(cfg.tWTR) + calcCK(cfg.tRP) + 5){
              dqOut := False
              dqsOut := False
            }

            when(timer.wkCnt === 0){
              when(refreshCount > 8){
                workState := REFRESH
                timer.wkCnt := calcCK(cfg.tRFC)
              }otherwise{
                when(sysIO.dataWr.valid){
                  timer.wkCnt := calcCK(cfg.tRCD)+1 + 6 + 4 + calcCK(cfg.tWTR) + calcCK(cfg.tRP) + 1 + 5
                }otherwise{
                  workState:=IDLE
                }
              }
            }
          }
        }


      } otherwise {
        // init
        switch(initState) {
          is(WAIT0) {
            when(timer.tick) {
              phyIO.rst_n := True

              timer.counter := 500 * cfg.clkFreq // wait for 500us
              initState := CKE
            }
          }
          is(CKE) {
            when(timer.tick) {
              phyIO.cke := True

              if (calcCK(cfg.tRFC + 10000) > 5) {
                timer.counter := calcCK(cfg.tRFC + 10000)
              } else {
                timer.counter := 5
              }
              initState := MRS2
            }
          }
          is(MRS2) { // set MRS2 to 0008
            when(timer.tick) {
              phyIO.cas := False
              phyIO.ras := False
              phyIO.we := False

              phyIO.address := 0x08
              phyIO.bank := U"010"

              timer.counter := 4
              initState := MRS3
            }
          }
          is(MRS3) { // set MRS3 to 0
            when(timer.tick) {
              phyIO.cas := False
              phyIO.ras := False
              phyIO.we := False

              phyIO.address := 0
              phyIO.bank := U"011"

              timer.counter := 4
              initState := MRS1
            }
          }
          is(MRS1) { //set MRS1 to 0001
            when(timer.tick) {
              phyIO.cas := False
              phyIO.ras := False
              phyIO.we := False

              phyIO.address := 0x01
              phyIO.bank := U"001"

              timer.counter := 4
              initState := MRS0
            }
          }
          is(MRS0) { // set MRS0 to 0320
            when(timer.tick) {
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
            when(timer.tick) {
              phyIO.we := False

              phyIO.address := 0x400

              timer.counter := cfg.nDLLK + cfg.nZQInit
              initState := WAIT1
            }
          }
          is(WAIT1) {
            when(timer.tick) {
              initFin := True

              timer.counter := calcCK(cfg.tREFI)
            }
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
  def main(args: Array[String]): Unit = {
    SDDR3GenCfg.generateVerilog(new slowDDR3(slowDDR3Cfg()))
  }
}