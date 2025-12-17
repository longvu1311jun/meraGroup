package org.report.backend.model;

public class EmployeeStatsDto {
  private String employeeName;
  private long tongKhach; // 总客户数
  private long tongLich; // 总预约数
  private long hoanThanhMuon; // 延迟完成
  private long hoanThanh; // 已完成
  private long quaHan; // 过期

  public EmployeeStatsDto() {
  }

  public EmployeeStatsDto(String employeeName) {
    this.employeeName = employeeName;
    this.tongKhach = 0;
    this.tongLich = 0;
    this.hoanThanhMuon = 0;
    this.hoanThanh = 0;
    this.quaHan = 0;
  }

  public String getEmployeeName() {
    return employeeName;
  }

  public void setEmployeeName(String employeeName) {
    this.employeeName = employeeName;
  }

  public long getTongKhach() {
    return tongKhach;
  }

  public void setTongKhach(long tongKhach) {
    this.tongKhach = tongKhach;
  }

  public long getTongLich() {
    return tongLich;
  }

  public void setTongLich(long tongLich) {
    this.tongLich = tongLich;
  }

  public long getHoanThanhMuon() {
    return hoanThanhMuon;
  }

  public void setHoanThanhMuon(long hoanThanhMuon) {
    this.hoanThanhMuon = hoanThanhMuon;
  }

  public long getHoanThanh() {
    return hoanThanh;
  }

  public void setHoanThanh(long hoanThanh) {
    this.hoanThanh = hoanThanh;
  }

  public long getQuaHan() {
    return quaHan;
  }

  public void setQuaHan(long quaHan) {
    this.quaHan = quaHan;
  }
}

