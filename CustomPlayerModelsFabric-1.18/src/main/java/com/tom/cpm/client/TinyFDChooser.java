package com.tom.cpm.client;

import java.io.File;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import com.tom.cpl.gui.elements.FileChooserPopup;
import com.tom.cpl.gui.elements.FileChooserPopup.FileFilter;
import com.tom.cpl.gui.elements.FileChooserPopup.NativeChooser;

public class TinyFDChooser implements NativeChooser {
	private FileChooserPopup fc;

	public TinyFDChooser(FileChooserPopup fc) {
		this.fc = fc;
	}

	@Override
	public File open() {
		String path = fc.getCurrentDirectory().getAbsolutePath() + "/";
		if(fc.getFilter() instanceof FileFilter) {
			FileFilter ff = (FileFilter) fc.getFilter();
			if(ff.isFolder()) {
				String sel = TinyFileDialogs.tinyfd_selectFolderDialog(fc.getTitle(), path);
				if(sel == null)return null;
				return new File(sel);
			} else if(ff.getExt() != null) {
				try (MemoryStack stack = MemoryStack.stackPush()) {
					PointerBuffer aFilterPatterns = stack.mallocPointer(1);

					aFilterPatterns.put(stack.UTF8("*." + ff.getExt()));

					aFilterPatterns.flip();

					String sel = fc.isSaveDialog() ?
							TinyFileDialogs.tinyfd_saveFileDialog(fc.getTitle(), path, aFilterPatterns, fc.getDesc()) :
								TinyFileDialogs.tinyfd_openFileDialog(fc.getTitle(), path, aFilterPatterns, fc.getDesc(), false);
					if(sel == null)return null;
					return new File(sel);
				}
			}
		}
		String sel = fc.isSaveDialog() ?
				TinyFileDialogs.tinyfd_saveFileDialog(fc.getTitle(), path, null, fc.getDesc()) :
					TinyFileDialogs.tinyfd_openFileDialog(fc.getTitle(), path, null, fc.getDesc(), false);
		if(sel == null)return null;
		return new File(sel);
	}
}
