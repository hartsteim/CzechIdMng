/**
 * Long polling service
 *
 * @author Vít Švanda
 * @since 9.7.7
 */
export default class LongPollingManager {

  static sendLongPollingRequest(entityId, service) {
    // Is pooling enabled? Do we have all required permissions
    if (!this._isLongPollingEnabled()) {
      this.canSendLongPollingRequest = false;
    }
    if (!this.canSendLongPollingRequest) {
      this.setState({longPollingInprogress: false});
    } else {

      // Abort controller send signal for cancel the request. Useful in situation when component is unmounted.
      const controller = new AbortController();
      if (this.state.requestControllers) {
        this.state.requestControllers.push(controller);
      }
      // I do not want wait to render, I need to send request ASAP
      this.setState({longPollingInprogress: true});
      service.sendLongPollingRequest(entityId, controller.signal).then(result => {
        if (this.canSendLongPollingRequest) {
          if (result && result.state === 'RUNNING') {
            // Change of entity was detected, we need to execute
            // refresh and create new long-polling reqeust.
            this.setState({longPollingInprogress: true}, () => {
              this._sendLongPollingRequest(entityId);
              // prevent to show loaging for long pooling refreshes
              this._refreshAll({ hideTableShowLoading: true });
            });
          } else if (result && result.state === 'NOT_EXECUTED') {
            // None change for entity was made. We will send next long-polling checking request
            this._sendLongPollingRequest(entityId);
          //  this._refreshAll();
          } else if (result && result.state === 'BLOCKED') {
            // Long pooling is blocked on BE!
            this.setState({longPollingInprogress: false,
              automaticRefreshOn: false}, () => {
              this.canSendLongPollingRequest = false;
            });
          }
        } else {
          this.setState({longPollingInprogress: false});
        }
      })
        .catch(error => {
          if (error.name === 'AbortError') {
            // AbortError is OK. Component was probably unmounted -> request could be aborted.
            this.canSendLongPollingRequest = false;
            this.setState({longPollingInprogress: false});
            return;
          }
          this.addError(error);
          this.canSendLongPollingRequest = false;
          this.setState({longPollingInprogress: false});
        });
    }
  }

  static toggleAutomaticRefresh() {
    const canSendLongPollingRequest = this.canSendLongPollingRequest;

    this.canSendLongPollingRequest = !canSendLongPollingRequest;
    this.setState({
      automaticRefreshOn: !canSendLongPollingRequest
    }, () => {
      if (this.canSendLongPollingRequest) {
        setTimeout(() => { // Why, timeout? Because I need to wait on the end of Switch animation.
          this._refreshAll();
        }, 200);
      }
    });
  }
}
