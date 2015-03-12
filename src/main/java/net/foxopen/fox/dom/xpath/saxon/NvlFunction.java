/*

Copyright (c) 2012, UK DEPARTMENT OF ENERGY AND CLIMATE CHANGE - 
                    ENERGY DEVELOPMENT UNIT (IT UNIT)
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, 
      this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright notice, 
      this list of conditions and the following disclaimer in the documentation 
      and/or other materials provided with the distribution.
    * Neither the name of the DEPARTMENT OF ENERGY AND CLIMATE CHANGE nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND 
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES 
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON 
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT 
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS 
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

$Id$

*/
package net.foxopen.fox.dom.xpath.saxon;


import net.sf.saxon.s9api.ExtensionFunction;
import net.sf.saxon.s9api.ItemType;
import net.sf.saxon.s9api.OccurrenceIndicator;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SequenceType;
import net.sf.saxon.s9api.XdmValue;


/**
 * Implementation of the Fox nvl() function. This mimics its Oracle namesake by returning the second argument if the
 * first is equivelant to null.
 */
public class NvlFunction 
implements ExtensionFunction  {
  
  /**
   * Only SaxonEnvironment may instantiate this object.
   */
  NvlFunction (){}
    
  public QName getName() {
    return new QName(SaxonEnvironment.FOX_NS_PREFIX, SaxonEnvironment.FOX_NS_URI, "nvl");      
  }

  public SequenceType getResultType() {
    return SequenceType.makeSequenceType(ItemType.ANY_ITEM, OccurrenceIndicator.ZERO_OR_MORE);
  }

  public SequenceType[] getArgumentTypes() {
    return new SequenceType[]{
      SequenceType.makeSequenceType(ItemType.ANY_ITEM, OccurrenceIndicator.ZERO_OR_ONE),
      SequenceType.makeSequenceType(ItemType.ANY_ITEM, OccurrenceIndicator.ZERO_OR_ONE)
    };
  }

  public XdmValue call(XdmValue[] xdmValues) {    
    return SaxonEnvironment.isValueNull(xdmValues[0]) ? xdmValues[1] : xdmValues[0];
  }
}