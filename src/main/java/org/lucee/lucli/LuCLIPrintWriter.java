package org.lucee.lucli;

public class LuCLIPrintWriter extends java.io.PrintWriter {
	
	public LuCLIPrintWriter(java.io.OutputStream out) {
        super(out);

	}

    @Override
    public void println(String x) {
        super.println("🚀" + x);
        super.flush();
    }
    
    
}
