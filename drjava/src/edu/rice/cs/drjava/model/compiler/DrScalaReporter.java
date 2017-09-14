/*BEGIN_COPYRIGHT_BLOCK
 *
 * Copyright (c) 2001-2012, JavaPLT group at Rice University (drjava@rice.edu)
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 *    * Neither the names of DrJava, DrScala, the JavaPLT group, Rice University, nor the
 *      names of its contributors may be used to endorse or promote products
 *      derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * This software is Open Source Initiative approved Open Source Software.
 * Open Source Initative Approved is a trademark of the Open Source Initiative.
 * 
 * This file is part of DrScala.  Download the current version of this project
 * from http://www.drscala.org/.
 * 
 * END_COPYRIGHT_BLOCK*/

package edu.rice.cs.drjava.model.compiler;

import java.util.LinkedList;

import scala.Function1;
import scala.Unit;
import scala.runtime.AbstractFunction1;
import scala.runtime.BoxedUnit;

import scala.tools.nsc.Settings;
import scala.tools.nsc.reporters.ConsoleReporter;
import scala.tools.nsc.reporters.Reporter;
import scala.reflect.internal.util.FakePos;
import scala.reflect.internal.util.Position;
import scala.reflect.internal.util.SourceFile;

import edu.rice.cs.drjava.model.DJError;
import edu.rice.cs.util.Log;

//import scala.tools.nsc.util.*;  /* { Position, NoPosition, SourceFile } */

/** DrJava Reporter class that extends scala.tools.nsc.ConsoleReporter.  The extension includes:
  * (i) a syntaxErrors table of type LinkedList<DJError> for logging all errors generated by the scalc compiler and 
  * (ii) an overridden print method that adds each scalac error message to the table. 
  * The table syntaxErrors is created by the caller.
  */
public class DrScalaReporter extends ConsoleReporter {
  
  public static final Log _log = new Log("GlobalModel.txt", false);
  
  /** Error table passed in from client. */
  final LinkedList<DJError> syntaxErrors;

  public DrScalaReporter(final LinkedList<DJError> errors) { 
    super(new Settings(new AbstractFunction1<String, BoxedUnit>() {
      public BoxedUnit apply(String msg) { 
//        error(new FakePos("scalac"), msg + "\n  scalac -help  gives more information");
        return BoxedUnit.UNIT;
      }
    }));
    _log.log("Binding syntaxErrors in " + this);
    syntaxErrors = errors; 
  }
  
  /* WHY IS THE RETURN TYPE void?  CONFLICTS WITH SCALA DOCUMENTATION which says it should return Unit. */
  @Override public void print(Position pos, String msg, Severity severity) {
    if (pos != null && pos.isDefined()) {
      /* msg has a corresponding source file */
      SourceFile source = pos.source();
      /* NOTE: line adjusted by one based on observation. Does Scala follow a different line numbering scheme? */
      DJError error = new DJError(source.file().file(), pos.line() - 1, pos.column(), msg, /* ! severity.equals(ERROR()) */ false);
      _log.log("Recording error " + error);
      syntaxErrors.add(error);
      /* pos.file() is a scala AbstractFile; pos.file().file() is the Java File backing it. */
    }
    else {
      /* pos is either null, NoPosition (a Scala object) or a FakePos (an instance of a Scala case class). 
       * No sourcefile is available in any of these cases. */
      DJError error = new DJError(null, -1, -1, msg, ! severity.equals(ERROR()));
      _log.log("Recording error " + error);
      syntaxErrors.add(error);
    }
    super.print(pos, msg, severity);
  }
}
  