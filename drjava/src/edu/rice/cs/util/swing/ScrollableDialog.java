/*BEGIN_COPYRIGHT_BLOCK
 *
 * This file is part of DrJava.  Download the current version of this project from http://www.drjava.org/
 * or http://sourceforge.net/projects/drjava/
 *
 * DrJava Open Source License
 * 
 * Copyright (C) 2001-2006 JavaPLT group at Rice University (javaplt@rice.edu).  All rights reserved.
 *
 * Developed by:   Java Programming Languages Team, Rice University, http://www.cs.rice.edu/~javaplt/
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated 
 * documentation files (the "Software"), to deal with the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and 
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * 
 *     - Redistributions of source code must retain the above copyright notice, this list of conditions and the 
 *       following disclaimers.
 *     - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the 
 *       following disclaimers in the documentation and/or other materials provided with the distribution.
 *     - Neither the names of DrJava, the JavaPLT, Rice University, nor the names of its contributors may be used to 
 *       endorse or promote products derived from this Software without specific prior written permission.
 *     - Products derived from this software may not be called "DrJava" nor use the term "DrJava" as part of their 
 *       names without prior written permission from the JavaPLT group.  For permission, write to javaplt@rice.edu.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO 
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE 
 * CONTRIBUTORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF 
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS 
 * WITH THE SOFTWARE.
 * 
 *END_COPYRIGHT_BLOCK*/

package edu.rice.cs.util.swing;

import edu.rice.cs.drjava.ui.MainFrame;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import java.io.Serializable;

/**
 * Manages a JDialog with a scrollable text area and a button panel.
 * @version $Id$
 */
public class ScrollableDialog implements Serializable {
  /** Default width for all ScrollableDialogs. */
  public static final int DEFAULT_WIDTH = 500;
  /** Default height for all ScrollableDialogs. */
  public static final int DEFAULT_HEIGHT = 400;
  /** JDialog managed by this component. */
  protected JDialog _dialog;
  /** JTextArea contained in a scroll pane in this dialog. */
  protected JTextArea _textArea;
  /** Panel of buttons at the bottom of this dialog. */
  protected JPanel _buttonPanel;
  
  /**
   * Creates a new ScrollableDialog with the default width and height.
   * @param parent Parent frame for this dialog
   * @param title Title for this dialog
   * @param header Message to display at the top of this dialog
   * @param text Text to insert into the scrollable JTextArea
   */
  public ScrollableDialog(JFrame parent, String title, String header, String text) {
    this(parent, title, header, text, DEFAULT_WIDTH, DEFAULT_HEIGHT);
  }
  
  /**
   * Creates a new ScrollableDialog.
   * @param parent Parent frame for this dialog
   * @param title Title for this dialog
   * @param header Message to display at the top of this dialog
   * @param text Text to insert into the scrollable JTextArea
   * @param width Width for this dialog
   * @param height Height for this dialog
   */
  public ScrollableDialog(JFrame parent, String title, String header, String text, int width, int height)
  {
    _dialog = new JDialog(parent, title, true);    
    Container content = _dialog.getContentPane();

    content.setLayout(new BorderLayout());

    // Create the text area
    _textArea = new JTextArea();
    _textArea.setEditable(false);
    _textArea.setText(text);
    
    // Arrange the dialog
    _dialog.setSize(width, height);
    
    // Add components
    JScrollPane textScroll = 
      new BorderlessScrollPane(_textArea, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                               JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    JPanel scrollWrapper = new JPanel(new BorderLayout(0,5));
    scrollWrapper.setBorder(new EmptyBorder(5,5,0,5));
    scrollWrapper.add(new JLabel(header),BorderLayout.NORTH);
    scrollWrapper.add(textScroll,BorderLayout.CENTER);
    JPanel bottomPanel = new JPanel(new BorderLayout());
    _buttonPanel = new JPanel(new GridLayout(1,0,5,5));
    bottomPanel.add(_buttonPanel,BorderLayout.EAST);
    bottomPanel.setBorder(new EmptyBorder(5,5,5,5));
    _addButtons();
    
    content.add(scrollWrapper, BorderLayout.CENTER);
    content.add(bottomPanel, BorderLayout.SOUTH);
    
    // This method is deprecated.  There are alternatives, but it is
    // probably best to let defer to the standard focus-management
    // policy rather than trying to customize it.
    //_textArea.requestDefaultFocus();
  }

  /**
   * Adds buttons to this dialog's button panel.
   * Subclasses can override this to add different buttons.
   */
  protected void _addButtons() {
    _buttonPanel.add(new JButton(_okAction));
  }

  /** A default "OK" action which disposes this dialog when invoked.
*/
  private Action _okAction = new AbstractAction("OK") {
    public void actionPerformed(ActionEvent e) {
      _dialog.dispose();
    }
  };

  /**
   * Sets the font for the text area in this dialog.
   * @param f New font for the text
   */
  public void setTextFont(Font f) {
    _textArea.setFont(f);
  }
  
  /** Shows this dialog. */
  public void show() {
    MainFrame.setPopupLoc(_dialog, _dialog.getOwner());
    _dialog.setVisible(true);
  }
}
