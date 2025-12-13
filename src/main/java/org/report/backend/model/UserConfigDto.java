package org.report.backend.model;

public class UserConfigDto {
  private PosUser posUser;
  private LarkNode larkNode;
  
  public UserConfigDto(PosUser posUser, LarkNode larkNode) {
    this.posUser = posUser;
    this.larkNode = larkNode;
  }
  
  public PosUser getPosUser() {
    return posUser;
  }
  
  public void setPosUser(PosUser posUser) {
    this.posUser = posUser;
  }
  
  public LarkNode getLarkNode() {
    return larkNode;
  }
  
  public void setLarkNode(LarkNode larkNode) {
    this.larkNode = larkNode;
  }
  
  public String getPosName() {
    return posUser != null ? posUser.getName() : "";
  }
  
  public String getLarkName() {
    return larkNode != null ? larkNode.getTitle() : "";
  }
  
  public String getBaseId() {
    return larkNode != null ? larkNode.getObjToken() : "";
  }
}

