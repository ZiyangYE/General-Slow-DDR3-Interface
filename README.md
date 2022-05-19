# General-Slow-DDR3-Interface
__\*This Repository is in development.\*__  
__\*The initial version is expected to released in 2022 June.\*__  

A general slow DDR3 interface.   
Very little resource consumption.  
Suits for all FPGAs with 1.5V IO voltage.  

## Specifications
* ___Designed to run at 100MHz DDR clock freqency.___  
*50MHz? That's OK.*  
*10MHz? OK, but refresh takes too much clocks.*  
*200MHz? Depends on the hardware and the FPGA.*  
*400MHz? It may not work. Whatever, take a shot.*  
* __Designed to work with LVCMOS IO__  
*The FPGA has differential IOs? That's better.*  
*The FPGA has SSTL/HSTLs? Higher frequency it may run at.*  
*The FPGA has DDRs? May run at double rate. **(Not supported yet)***  
*Only has LVCMOSs? It can work.*  
* __Very low resource consumption__  
*Wish to attach a DDR3 module to a CPLD? Try it.*
* __Write in SpinalHDL__  
*Future is coming.*

## Files
* __SLDDR3.scala__  
The SpinalHDL design file.  
Compile it to get the output verilog file.
* __SLDDR3.v__  
Pre-compiled verilog file.  
Designed to run at 100MHz with a 64Mx16b DDR3 chip.
* __tb.v__  
Testbench file.  
A DDR3 model is required to do the simulation.  
You may find it at Micron's website.  
<u>[DDR3 Model](https://media-www.micron.com/-/media/client/global/documents/products/sim-model/dram/ddr3/ddr3-sdram-verilog-model.zip?rev=925a8a05204e4b5c9c1364302de60126)</u>

## Timing
__\*TBD\*__