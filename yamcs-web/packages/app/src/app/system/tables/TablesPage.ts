import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { MatSort, MatTableDataSource } from '@angular/material';
import { Title } from '@angular/platform-browser';
import { Instance, Table } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';



@Component({
  templateUrl: './TablesPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TablesPage implements AfterViewInit {

  @ViewChild(MatSort)
  sort: MatSort;

  instance: Instance;

  displayedColumns = ['name'];

  dataSource = new MatTableDataSource<Table>();

  constructor(yamcs: YamcsService, title: Title) {
    title.setTitle('Tables');
    yamcs.getInstanceClient()!.getTables().then(tables => {
      this.dataSource.data = tables;
    });
    this.instance = yamcs.getInstance();
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
  }
}
