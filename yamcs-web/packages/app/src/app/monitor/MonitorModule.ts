import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { MonitorRoutingModule, routingComponents } from './MonitorRoutingModule';
import { MonitorPageTemplate } from './template/MonitorPageTemplate';
import { ProcessorInfoComponent } from './template/ProcessorInfoComponent';
import { EventSeverity } from './events/EventSeverity';
import { DisplayNavigator } from './displays/DisplayNavigator';
import { SaveLayoutDialog } from './displays/SaveLayoutDialog';
import { LayoutComponent } from './displays/LayoutComponent';
import { AlarmDetail } from './alarms/AlarmDetail';
import { MonitorToolbar } from './template/MonitorToolbar';
import { PageContentHost } from './ext/PageContentHost';
import { CreateEventDialog } from './events/CreateEventDialog';
import { TimelineTooltip } from './archive/TimelineTooltip';
import { DownloadDumpDialog } from './archive/DownloadDumpDialog';
import { StartReplayDialog } from './template/StartReplayDialog';

const dialogComponents = [
  CreateEventDialog,
  DownloadDumpDialog,
  SaveLayoutDialog,
  StartReplayDialog,
];

@NgModule({
  imports: [
    SharedModule,
    MonitorRoutingModule,
  ],
  declarations: [
    routingComponents,
    dialogComponents,
    AlarmDetail,
    DisplayNavigator,
    EventSeverity,
    LayoutComponent,
    MonitorPageTemplate,
    PageContentHost,
    ProcessorInfoComponent,
    TimelineTooltip,
  ],
  exports: [
    MonitorPageTemplate,
    MonitorToolbar,
  ],
  entryComponents: [
    dialogComponents,
    TimelineTooltip,
  ]
})
export class MonitorModule {
}
