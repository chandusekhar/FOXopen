package net.foxopen.fox.entrypoint;

import net.foxopen.fox.ContextLabel;
import net.foxopen.fox.FoxRequest;
import net.foxopen.fox.dom.DOM;
import net.foxopen.fox.ex.ExInternal;

import java.util.Iterator;
import java.util.Map;

public class ParamsDOMUtils {

  public static DOM defaultEmptyDOM() {
    return DOM.createDocument(ContextLabel.PARAMS.asString());
  }

  public static DOM paramsDOMFromXMLString(String pXMLString) {
    DOM lParsedDOM = DOM.createDocumentFromXMLString(pXMLString);
    DOM lNewParamsDOM = defaultEmptyDOM();
    lParsedDOM.copyContentsTo(lNewParamsDOM);
    return lNewParamsDOM;
  }

  public static DOM paramsDOMFromRequest(FoxRequest pFoxRequest) {
    //Only create the params DOM from GET params - other methods imply a non-standard route in
    if("GET".equals(pFoxRequest.getHttpRequest().getMethod())) {
      return convertParamsMapToContainerDOM(pFoxRequest.getHttpRequest().getParameterMap());
    }
    else {
      return defaultEmptyDOM();
    }
  }

  private static DOM convertParamsMapToContainerDOM(Map mapParams)
  throws ExInternal {
    //------------------------------------------------------------------------
    // Convert web-style call paremeters to XML elements
    //------------------------------------------------------------------------
    DOM lContainerDOM = defaultEmptyDOM();
    for (Iterator iter = mapParams.entrySet().iterator(); iter.hasNext(); ) {
      Map.Entry paramEntry = (Map.Entry)iter.next();
      String paramName     = (String)paramEntry.getKey();
      String paramValues[] = (paramEntry.getValue() instanceof String ? new String[] { (String)paramEntry.getValue() } : (String[])paramEntry.getValue());

      for (int i = 0; i < paramValues.length; i++) {
        lContainerDOM.addElem(paramName, paramValues[i]);
      }
    }

    return lContainerDOM;
  }
}
