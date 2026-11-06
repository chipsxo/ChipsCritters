package dev.chipsxo.chipscritters;

/** Automatic placement stays in the top-center lane and clears the main panel when their bounds meet. */
final class WaypointPlacement {
	private WaypointPlacement() {}
 static int centerText(int panelWidth,int textWidth){return Math.max(0,(panelWidth-textWidth)/2);}

	static int x(int screenWidth, int width, int savedX) {
		int limit = Math.max(0, screenWidth - width);
		return savedX < 0 ? limit / 2 : Math.min(savedX, limit);
	}

	static double scale(int screenWidth, int screenHeight, int mainHeight, int width, int height, double requested) {
		return Math.min(requested, Math.min((screenWidth - 16.0) / width,
				Math.max(1, screenHeight - 22.0 - mainHeight) / height));
	}

	static int y(int screenW, int height, boolean sidebar, int margin, int mainX, int mainY, int mainW, int mainH,
			int x, int width, int panelH, int savedY) {
		int limit = Math.max(0, height - panelH);
		if (savedY >= 0) return Math.min(savedY, limit);
		int top = Math.min(margin, limit);
		int firstClear = -1;
		for (int y = top; y <= limit; y++) {
			if (x < mainX + mainW && x + width > mainX && y < mainY + mainH + 3 && y + panelH + 3 > mainY) continue;
			if (firstClear < 0) firstClear = y;
			// Reserve the usual left checklist and right sidebar lanes. Third-party HUDs can be dragged independently.
			boolean checklist = x < screenW / 3 && y + panelH > height / 5 && y < height * 2 / 3;
			boolean scores = sidebar && x + width > screenW * 3 / 4 && y + panelH > height / 4 && y < height * 3 / 4;
			if (!checklist && !scores) return y;
		}
		return firstClear >= 0 ? firstClear : top;
	}
}
