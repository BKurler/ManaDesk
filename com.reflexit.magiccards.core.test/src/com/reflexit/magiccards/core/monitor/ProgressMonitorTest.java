/*******************************************************************************
 * Copyright (c) 2026 Rémi Dutil.
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v2.0 which accompanies
 * this distribution, and is available at:
 *   https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.html
 *
 * Contributors:
 *     Rémi Dutil - created for ManaDesk
 *******************************************************************************/
package com.reflexit.magiccards.core.monitor;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

/**
 * The progress monitors the long jobs (database update, set writes) report through:
 * the forwarding wrapper, the do-nothing monitor and {@link SubCoreProgressMonitor}, which
 * hands a fixed share of its parent's work to a nested task. Pure logic; works as plain JUnit.
 */
public class ProgressMonitorTest extends TestCase {

	/** Records every call it receives. */
	private static class Recorder implements ICoreProgressMonitor {
		double worked;
		int begun;
		int total;
		int done;
		boolean canceled;
		String taskName;
		String beginName;
		final List<String> subTasks = new ArrayList<String>();

		@Override
		public void beginTask(String name, int totalWork) {
			begun++;
			beginName = name;
			total = totalWork;
		}

		@Override
		public void done() {
			done++;
		}

		@Override
		public void internalWorked(double work) {
			worked += work;
		}

		@Override
		public boolean isCanceled() {
			return canceled;
		}

		@Override
		public void setCanceled(boolean value) {
			canceled = value;
		}

		@Override
		public void setTaskName(String name) {
			taskName = name;
		}

		@Override
		public void subTask(String name) {
			subTasks.add(name);
		}

		@Override
		public void worked(int work) {
			worked += work;
		}
	}

	private final Recorder parent = new Recorder();

	// ---- the do-nothing monitor

	public void testNoneMonitorIgnoresEverythingAndCannotBeCanceled() {
		ICoreProgressMonitor none = ICoreProgressMonitor.NONE;
		none.beginTask("t", 10);
		none.worked(3);
		none.internalWorked(1.5);
		none.subTask("s");
		none.setTaskName("n");
		none.done();
		none.setCanceled(true);
		assertFalse(none.isCanceled());
		assertEquals(-1, ICoreProgressMonitor.UNKNOWN);
	}

	// ---- the forwarding wrapper

	public void testWrapperForwardsEveryCall() {
		ProgressMonitorWrapper w = new ProgressMonitorWrapper(parent) {
		};
		w.beginTask("main", 50);
		w.worked(5);
		w.internalWorked(2.5);
		w.subTask("sub");
		w.setTaskName("name");
		w.setCanceled(true);
		w.done();
		assertEquals("main", parent.beginName);
		assertEquals(50, parent.total);
		assertEquals(7.5, parent.worked, 0.0001);
		assertEquals("sub", parent.subTasks.get(0));
		assertEquals("name", parent.taskName);
		assertTrue(parent.canceled);
		assertTrue(w.isCanceled());
		assertEquals(1, parent.done);
		assertSame(parent, w.getWrappedProgressMonitor());
	}

	// ---- the sub monitor

	public void testSubMonitorScalesItsWorkToItsShareOfTheParent() {
		SubCoreProgressMonitor sub = new SubCoreProgressMonitor(parent, 40);
		sub.beginTask("ignored", 100);
		assertEquals("the parent is not started again", 0, parent.begun);
		sub.worked(50);
		assertEquals(20.0, parent.worked, 0.0001);
		sub.worked(25);
		assertEquals(30.0, parent.worked, 0.0001);
		sub.done();
		assertEquals("done sends the rest of the share", 40.0, parent.worked, 0.0001);
		assertEquals("the parent is not finished by its child", 0, parent.done);
	}

	public void testSubMonitorNeverSendsMoreThanItsShare() {
		SubCoreProgressMonitor sub = new SubCoreProgressMonitor(parent, 10);
		sub.beginTask("t", 4);
		sub.worked(4);
		sub.worked(4); // more than announced
		sub.internalWorked(100);
		sub.done();
		assertEquals(10.0, parent.worked, 0.0001);
	}

	public void testSubMonitorDoneAloneSendsTheWholeShare() {
		SubCoreProgressMonitor sub = new SubCoreProgressMonitor(parent, 25);
		sub.beginTask("t", 10);
		sub.done();
		assertEquals(25.0, parent.worked, 0.0001);
	}

	public void testSubMonitorWithoutAnnouncedWorkOnlyReportsOnDone() {
		SubCoreProgressMonitor sub = new SubCoreProgressMonitor(parent, 30);
		sub.beginTask("t", 0); // scale 0: progress inside is off
		sub.worked(5);
		assertEquals(0.0, parent.worked, 0.0001);
		sub.done();
		assertEquals(30.0, parent.worked, 0.0001);
		SubCoreProgressMonitor neg = new SubCoreProgressMonitor(parent, 10);
		neg.beginTask("t", -5);
		neg.done();
		assertEquals(40.0, parent.worked, 0.0001);
	}

	public void testSubMonitorIgnoresNegativeWorkAndNegativeShare() {
		SubCoreProgressMonitor sub = new SubCoreProgressMonitor(parent, 10);
		sub.beginTask("t", 10);
		sub.worked(-3);
		assertEquals(0.0, parent.worked, 0.0001);
		SubCoreProgressMonitor none = new SubCoreProgressMonitor(parent, -5);
		none.beginTask("t", 10);
		none.worked(10);
		none.done();
		assertEquals("a negative share is no share", 0.0, parent.worked, 0.0001);
	}

	public void testSubMonitorIgnoresWorkOutsideItsTask() {
		SubCoreProgressMonitor sub = new SubCoreProgressMonitor(parent, 10);
		sub.worked(5); // not begun
		assertEquals(0.0, parent.worked, 0.0001);
		sub.done(); // a done without a begin is ignored
		assertEquals(0.0, parent.worked, 0.0001);
		sub.beginTask("t", 10);
		sub.done();
		assertEquals(10.0, parent.worked, 0.0001);
		sub.done(); // one done too many
		sub.worked(5);
		assertEquals(10.0, parent.worked, 0.0001);
	}

	public void testSubMonitorNestedBeginTasksAreIgnored() {
		SubCoreProgressMonitor sub = new SubCoreProgressMonitor(parent, 20);
		sub.beginTask("outer", 10);
		sub.beginTask("inner", 1000); // does not change the scale
		sub.worked(5);
		assertEquals("work reported inside a nested task is not counted", 0.0, parent.worked, 0.0001);
		sub.done(); // closes the inner one only: nothing is sent yet
		assertEquals(0.0, parent.worked, 0.0001);
		sub.worked(5); // back in the outer task: half of the work, so half of the share
		assertEquals(10.0, parent.worked, 0.0001);
		sub.done(); // closes the outer one: the rest is sent
		assertEquals(20.0, parent.worked, 0.0001);
	}

	public void testSubMonitorSubTaskLabels() {
		SubCoreProgressMonitor sub = new SubCoreProgressMonitor(parent, 10);
		sub.beginTask("main", 10);
		sub.subTask("reading");
		assertEquals("reading", parent.subTasks.get(0));
		sub.done();
		assertEquals("the label is cleared at the end", "", parent.subTasks.get(parent.subTasks.size() - 1));
	}

	public void testSubMonitorWithoutSubTaskDoesNotClearAnything() {
		SubCoreProgressMonitor sub = new SubCoreProgressMonitor(parent, 10);
		sub.beginTask("main", 10);
		sub.done();
		assertTrue(parent.subTasks.isEmpty());
	}

	public void testSubMonitorSuppressedLabels() {
		SubCoreProgressMonitor sub = new SubCoreProgressMonitor(parent, 10, SubCoreProgressMonitor.SUPPRESS_SUBTASK_LABEL);
		sub.beginTask("main", 10);
		sub.subTask("hidden");
		sub.done();
		assertTrue(parent.subTasks.isEmpty());
	}

	public void testSubMonitorPrependsTheMainLabel() {
		SubCoreProgressMonitor sub = new SubCoreProgressMonitor(parent, 10, SubCoreProgressMonitor.PREPEND_MAIN_LABEL_TO_SUBTASK);
		sub.beginTask("Writing", 10);
		sub.subTask("set A");
		assertEquals("Writing set A", parent.subTasks.get(0));
		SubCoreProgressMonitor noLabel = new SubCoreProgressMonitor(parent, 10, SubCoreProgressMonitor.PREPEND_MAIN_LABEL_TO_SUBTASK);
		noLabel.beginTask("", 10);
		noLabel.subTask("plain");
		assertEquals("an empty main label is not prepended", "plain", parent.subTasks.get(1));
	}

	public void testSubMonitorForwardsCancelAndTaskName() {
		SubCoreProgressMonitor sub = new SubCoreProgressMonitor(parent, 10);
		assertFalse(sub.isCanceled());
		parent.setCanceled(true);
		assertTrue("a cancel of the parent reaches the child", sub.isCanceled());
		sub.setCanceled(false);
		assertFalse(parent.isCanceled());
		sub.setTaskName("(3/10) Writing");
		assertEquals("(3/10) Writing", parent.taskName);
	}

	public void testSubMonitorsCanBeStacked() {
		// 100 for the parent: the first half goes to a child, which gives a quarter of it to a grandchild
		SubCoreProgressMonitor child = new SubCoreProgressMonitor(parent, 50);
		child.beginTask("child", 4);
		SubCoreProgressMonitor grand = new SubCoreProgressMonitor(child, 1);
		grand.beginTask("grand", 10);
		grand.worked(10);
		grand.done();
		assertEquals("one of the child's four units is 12.5 of the parent", 12.5, parent.worked, 0.0001);
		child.worked(3);
		child.done();
		assertEquals(50.0, parent.worked, 0.0001);
	}
}
