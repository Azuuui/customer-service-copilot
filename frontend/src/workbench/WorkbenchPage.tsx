import { AnnouncementRail } from "./AnnouncementRail";
import { OperationsRail } from "./OperationsRail";
import { QueryPanel } from "./QueryPanel";

export function WorkbenchPage() {
  return <div className="workbench"><AnnouncementRail /><QueryPanel /><OperationsRail /></div>;
}
