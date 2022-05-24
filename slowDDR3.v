// Generator : SpinalHDL v1.7.0a    git head : 150a9b9067020722818dfb17df4a23ac712a7af8
// Component : slowDDR3

`timescale 1ns/1ps

module slowDDR3 (
  input               inv_clk,
  output reg [13:0]   phyIO_address,
  output reg [2:0]    phyIO_bank,
  output              phyIO_cs,
  output reg          phyIO_cas,
  output reg          phyIO_ras,
  output reg          phyIO_we,
  output reg          phyIO_clk_p,
  output reg          phyIO_clk_n,
  output reg          phyIO_cke,
  output              phyIO_odt,
  output reg          phyIO_rst_n,
  output     [1:0]    phyIO_dm,
   inout     [15:0]   phyIO_dq,
   inout     [1:0]    phyIO_dqs_p,
   inout     [1:0]    phyIO_dqs_n,
  output              sysIO_dataRd_valid,
  input               sysIO_dataRd_ready,
  output     [15:0]   sysIO_dataRd_payload,
  input               sysIO_dataWr_valid,
  output              sysIO_dataWr_ready,
  input      [15:0]   sysIO_dataWr_payload,
  output reg          sysIO_initFin,
  input               clk,
  input               resetn
);
  localparam InitState_WAIT0 = 3'd0;
  localparam InitState_CKE = 3'd1;
  localparam InitState_MRS2 = 3'd2;
  localparam InitState_MRS3 = 3'd3;
  localparam InitState_MRS1 = 3'd4;
  localparam InitState_MRS0 = 3'd5;
  localparam InitState_ZQCL = 3'd6;
  localparam InitState_WAIT1 = 3'd7;

  reg                 _zz_phyIO_dqs_n;
  reg                 _zz_phyIO_dqs_p;
  reg                 _zz_phyIO_dq;
  wire       [15:0]   dqReg;
  wire       [1:0]    dqsWire;
  wire                dqOut;
  reg        [2:0]    initState_1;
  reg        [23:0]   timer_counter;
  wire                timer_tick;
  reg                 clkGen_clk;
  reg                 userReset;
  reg                 dqsGen_clk;
  reg        [1:0]    dqsGen_dqsReg;
  `ifndef SYNTHESIS
  reg [39:0] initState_1_string;
  `endif


  assign phyIO_dq = _zz_phyIO_dq ? dqReg : 16'bzzzzzzzzzzzzzzzz;
  assign phyIO_dqs_p = _zz_phyIO_dqs_p ? dqsWire : 2'bzz;
  assign phyIO_dqs_n = _zz_phyIO_dqs_n ? (~ dqsWire) : 2'bzz;
  `ifndef SYNTHESIS
  always @(*) begin
    case(initState_1)
      InitState_WAIT0 : initState_1_string = "WAIT0";
      InitState_CKE : initState_1_string = "CKE  ";
      InitState_MRS2 : initState_1_string = "MRS2 ";
      InitState_MRS3 : initState_1_string = "MRS3 ";
      InitState_MRS1 : initState_1_string = "MRS1 ";
      InitState_MRS0 : initState_1_string = "MRS0 ";
      InitState_ZQCL : initState_1_string = "ZQCL ";
      InitState_WAIT1 : initState_1_string = "WAIT1";
      default : initState_1_string = "?????";
    endcase
  end
  `endif

  always @(*) begin
    _zz_phyIO_dqs_n = 1'b0;
    if(dqOut) begin
      _zz_phyIO_dqs_n = 1'b1;
    end
  end

  always @(*) begin
    _zz_phyIO_dqs_p = 1'b0;
    if(dqOut) begin
      _zz_phyIO_dqs_p = 1'b1;
    end
  end

  always @(*) begin
    _zz_phyIO_dq = 1'b0;
    if(dqOut) begin
      _zz_phyIO_dq = 1'b1;
    end
  end

  assign phyIO_cs = 1'b0;
  assign phyIO_odt = 1'b0;
  assign phyIO_dm = 2'b00;
  assign dqReg = 16'h0;
  assign dqOut = 1'b0;
  assign sysIO_dataRd_payload = 16'h0;
  assign sysIO_dataRd_valid = 1'b0;
  assign sysIO_dataWr_ready = 1'b0;
  assign timer_tick = (timer_counter == 24'h0);
  assign dqsWire = dqsGen_dqsReg;
  always @(posedge clk or negedge resetn) begin
    if(!resetn) begin
      phyIO_address <= 14'h3fff;
      phyIO_bank <= 3'b000;
      phyIO_cas <= 1'b1;
      phyIO_ras <= 1'b1;
      phyIO_we <= 1'b1;
      phyIO_clk_p <= 1'b0;
      phyIO_clk_n <= 1'b1;
      phyIO_cke <= 1'b0;
      phyIO_rst_n <= 1'b0;
      sysIO_initFin <= 1'b0;
      initState_1 <= InitState_WAIT0;
      timer_counter <= 24'h004e20;
      clkGen_clk <= 1'b0;
      userReset <= 1'b0;
    end else begin
      timer_counter <= (timer_counter - 24'h000001);
      clkGen_clk <= (! clkGen_clk);
      phyIO_clk_p <= clkGen_clk;
      phyIO_clk_n <= (! clkGen_clk);
      userReset <= 1'b1;
      phyIO_cas <= 1'b1;
      phyIO_ras <= 1'b1;
      phyIO_we <= 1'b1;
      if(!sysIO_initFin) begin
        case(initState_1)
          InitState_WAIT0 : begin
            if(timer_tick) begin
              phyIO_rst_n <= 1'b1;
              timer_counter <= 24'h00c350;
              initState_1 <= InitState_CKE;
            end
          end
          InitState_CKE : begin
            if(timer_tick) begin
              phyIO_cke <= 1'b1;
              timer_counter <= 24'h000011;
              initState_1 <= InitState_MRS2;
            end
          end
          InitState_MRS2 : begin
            if(timer_tick) begin
              phyIO_cas <= 1'b0;
              phyIO_ras <= 1'b0;
              phyIO_we <= 1'b0;
              phyIO_address <= 14'h0;
              phyIO_bank <= 3'b010;
              timer_counter <= 24'h000004;
              initState_1 <= InitState_MRS3;
            end
          end
          InitState_MRS3 : begin
            if(timer_tick) begin
              phyIO_cas <= 1'b0;
              phyIO_ras <= 1'b0;
              phyIO_we <= 1'b0;
              phyIO_address <= 14'h0;
              phyIO_bank <= 3'b011;
              timer_counter <= 24'h000004;
              initState_1 <= InitState_MRS1;
            end
          end
          InitState_MRS1 : begin
            if(timer_tick) begin
              phyIO_cas <= 1'b0;
              phyIO_ras <= 1'b0;
              phyIO_we <= 1'b0;
              phyIO_address <= 14'h0044;
              phyIO_bank <= 3'b001;
              timer_counter <= 24'h000004;
              initState_1 <= InitState_MRS0;
            end
          end
          InitState_MRS0 : begin
            if(timer_tick) begin
              phyIO_cas <= 1'b0;
              phyIO_ras <= 1'b0;
              phyIO_we <= 1'b0;
              phyIO_address <= 14'h0320;
              phyIO_bank <= 3'b000;
              timer_counter <= 24'h00000c;
              initState_1 <= InitState_ZQCL;
            end
          end
          InitState_ZQCL : begin
            if(timer_tick) begin
              phyIO_we <= 1'b0;
              phyIO_address <= 14'h0400;
              timer_counter <= 24'h000200;
              initState_1 <= InitState_WAIT1;
            end
          end
          default : begin
            if(timer_tick) begin
              sysIO_initFin <= 1'b1;
              timer_counter <= 24'h00030c;
            end
          end
        endcase
      end
    end
  end

  always @(posedge inv_clk or negedge userReset) begin
    if(!userReset) begin
      dqsGen_clk <= 1'b0;
      dqsGen_dqsReg <= 2'b00;
    end else begin
      dqsGen_clk <= (! dqsGen_clk);
      dqsGen_dqsReg <= (dqsGen_clk ? 2'b11 : 2'b00);
    end
  end


endmodule
