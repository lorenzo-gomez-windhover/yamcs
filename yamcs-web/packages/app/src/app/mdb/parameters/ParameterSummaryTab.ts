import { Component, ChangeDetectionStrategy } from '@angular/core';
import { Parameter, Instance, ParameterValue } from '@yamcs/client';
import { ActivatedRoute } from '@angular/router';
import { YamcsService } from '../../core/services/YamcsService';
import { Observable } from 'rxjs/Observable';
import { State } from '../../app.reducers';
import { Store } from '@ngrx/store';
import { selectCurrentInstance } from '../../core/store/instance.selectors';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { Subscription } from 'rxjs/Subscription';

@Component({
  templateUrl: './ParameterSummaryTab.html',
  styleUrls: ['./ParameterSummaryTab.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterSummaryTab {

  instance$: Observable<Instance>;
  parameter$: Promise<Parameter>;

  parameterValue$ = new BehaviorSubject<ParameterValue | null>(null);
  parameterValueSubscription: Subscription;

  constructor(route: ActivatedRoute, yamcs: YamcsService, store: Store<State>) {
    this.instance$ = store.select(selectCurrentInstance);
    const qualifiedName = route.parent!.snapshot.paramMap.get('qualifiedName')!;
    this.parameter$ = yamcs.getSelectedInstance().getParameter(qualifiedName);

    yamcs.getSelectedInstance().getParameterValueUpdates({
      id: [{ name: qualifiedName }],
      abortOnInvalid: false,
      sendFromCache: true,
      subscriptionId: -1,
      updateOnExpiration: true,
    }).then(res => {
      this.parameterValueSubscription = res.parameterValues$.subscribe(pvals => {
        this.parameterValue$.next(pvals[0]);
      });
    });
  }
}
