SYNTHESIJER ?= ../../bin

SOURCES = EchoTest.java RS232C_RX_Wrapper.java RS232C_TX_Wrapper.java ToUpper.java
VHDL_SOURCES = $(SOURCES:.java=.vhd)
VERILOG_SOURCES = $(SOURCES:.java=.v)
OPT = --chaining

all: hdl exstick microboard

hdl: $(SOURCES)
	java -cp $(SYNTHESIJER):. synthesijer.Main --vhdl --verilog $(OPT) $(SOURCES)

exstick: EchoTestTop.java 
	javac -cp $(SYNTHESIJER):. EchoTestTop.java
	java -cp $(SYNTHESIJER):. EchoTestTop

microboard: MicroboardTop.java 
	javac -cp $(SYNTHESIJER):. MicroboardTop.java
	java -cp $(SYNTHESIJER):. MicroboardTop

clean:
	-rm -f *.class
	-rm -f $(VHDL_SOURCES)
	-rm -f $(VERILOG_SOURCES)
	-rm -f *.xml
	-rm -f *.dot
	-rm -f *.o
	-rm -f sim
	-rm -f *.vcd
	-rm -f *.txt
	-rm -f work-obj93.cf
	-rm -f top.vhd top.v
	-rm -f microboard_top.vhd, microboard_top.v
