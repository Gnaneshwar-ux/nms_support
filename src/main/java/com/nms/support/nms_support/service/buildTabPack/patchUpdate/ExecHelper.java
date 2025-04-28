package com.nms.support.nms_support.service.buildTabPack.patchUpdate;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class ExecHelper {
	public static int exec(String... args) {
		try {
			return exec(args, 0L);
		} catch (TimeoutException e) {
			throw new RuntimeException(e);
		}
	}

	public static int exec(List<String> args) {
		String[] argsAr = new String[args.size()];
		args.toArray(argsAr);
		return exec(argsAr);
	}

	public static int exec(String[] args, long timeout) throws TimeoutException {
		try {
			final Process process = Runtime.getRuntime().exec(args);
			Thread t1 = new Thread() {
				public void run() {
					try {
						InputStream is = process.getInputStream();
						int b;
						while ((b = is.read()) >= 0) {
							System.out.write(b);
						}
					} catch (Exception exception) {
					}
				}
			};

			Thread t2 = new Thread() {
				public void run() {
					try {
						InputStream is = process.getErrorStream();
						int b;
						while ((b = is.read()) >= 0) {
							System.err.write(b);
						}
					} catch (Exception exception) {
					}
				}
			};

			t1.start();
			t2.start();
			Worker worker = new Worker(process);
			worker.start();
			try {
				worker.join(timeout);
			} catch (InterruptedException e) {
				worker.interrupt();
				Thread.currentThread().interrupt();
				throw new RuntimeException(e.getMessage());
			}
			if (worker.exit == null) {
				throw new TimeoutException();
			}
			return worker.exit.intValue();
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage());
		}
	}

	private static class Worker extends Thread {
		private final Process process;
		private Integer exit;

		private Worker(Process process) {
			this.process = process;
		}

		public void run() {
			try {
				this.exit = Integer.valueOf(this.process.waitFor());
			} catch (InterruptedException interruptedException) {
			}
		}
	}
}