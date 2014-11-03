package org.vaadin.teemu.clara;

import org.junit.Test;
import org.vaadin.artur.html.Clara;

import com.vaadin.ui.Component;

public class PotusTest {

	@Test
	public void testPotus() {
		Component c = Clara.create("potus.html", new PotusCrud(), this);
		System.out.println("Done");
	}
	
	@Test
	public void testView() {
		System.out.println(new PotusCrudView().getRoot());
	}
}
