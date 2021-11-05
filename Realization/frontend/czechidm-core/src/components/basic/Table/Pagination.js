import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import $ from 'jquery';
//
import { withStyles } from '@material-ui/core/styles';
//
import AbstractContextComponent from '../AbstractContextComponent/AbstractContextComponent';
import { SearchParameters } from '../../../domain';

const styles = theme => ({
  root: {
    padding: '10px 15px',
    borderTop: `1px solid ${ theme.palette.divider }`,
    borderBottomRightRadius: theme.shape.borderRadius,
    borderBottomLeftRadius: theme.shape.borderRadius,
    '& .pagination > li > a:focus': {
      color: theme.palette.secondary.main,
    },
    '& .pagination > li > a:hover': {
      color: theme.palette.secondary.main,
    },
    '& .pagination > .active > a': {
      color: theme.palette.secondary.contrastText,
      borderColor: theme.palette.secondary.main,
      backgroundColor: theme.palette.secondary.main,
      '&:focus': {
        color: theme.palette.secondary.contrastText,
        backgroundColor: theme.palette.secondary.main,
      },
      '&:hover': {
        color: theme.palette.secondary.contrastText,
        backgroundColor: theme.palette.secondary.main,
      }
    }
  },
  input: {
    '&.form-control': {
      borderRadius: theme.shape.borderRadius,
      borderColor: theme.palette.type === 'light'
        ? 'rgba(0, 0, 0, 0.23)'
        : 'rgba(255, 255, 255, 0.23)', // ~ hardcoded somewhere in material text field
      padding: 1,
      '&:focus': {
        boxShadow: 'none',
        borderColor: theme.palette.primary.main,
        borderWidth: 2,
        outline: 0,
        padding: 0
      }
    }
  },
  select: {
    width: 55,
    margin: '0 0 0 5px',
    padding: 1,
    backgroundColor: theme.palette.background.paper,
    borderWidth: 1,
    borderStyle: 'solid',
    borderColor: theme.palette.type === 'light'
      ? 'rgba(0, 0, 0, 0.23)'
      : 'rgba(255, 255, 255, 0.23)', // ~ hardcoded somewhere in material text field
    borderRadius: theme.shape.borderRadius,
    '&:focus': {
      boxShadow: 'none',
      borderColor: theme.palette.primary.main,
      borderWidth: 2,
      outline: 0,
      padding: 0
    }
  }
});

/**
 * Pagination for table etc. Supports:
 * - shows information about rendered records
 * - change current page by links and directly
 * - change page size
 *
 * @author Radek Tomiška
 */
class Pagination extends AbstractContextComponent {

  constructor(props) {
    super(props);
    this.state = {
      currentPage: this.props.page,
      changePage: this.props.page + 1,
      currentSize: this.props.size || SearchParameters.getDefaultSize()
    };
  }

  UNSAFE_componentWillReceiveProps(nextProps) {
    this.setState({
      currentPage: nextProps.page,
      changePage: nextProps.page + 1,
      currentSize: nextProps.size || SearchParameters.getDefaultSize()
    }, () => {
      if (nextProps.total && (nextProps.total <= (nextProps.page * nextProps.size))) {
        // index of bound - load last page
        this.setPage(this.getMaxPage(nextProps.total));
      }
    });
  }

  next(event) {
    if (event) {
      event.preventDefault();
    }
    this.setPage(this.state.currentPage + 1);
  }

  prev(event) {
    if (event) {
      event.preventDefault();
    }
    this.setPage(this.state.currentPage - 1);
  }

  hasPrev() {
    return this.state.currentPage > 0;
  }

  hasNext() {
    const { total } = this.props;
    const { currentPage, currentSize } = this.state;
    //
    const newFrom = (currentPage * currentSize) + currentSize;
    return newFrom < total;
  }

  getMaxPage(total = null) {
    const _total = total || this.props.total;
    const { currentSize } = this.state;
    return Math.ceil(_total / currentSize) - 1;
  }

  setPage(page, event) {
    if (event) {
      event.preventDefault();
    }
    //
    const { total, paginationHandler } = this.props;
    const { currentPage, currentSize } = this.state;
    //
    if (!page) {
      page = 0;
    }
    if (page > this.getMaxPage()) {
      page = this.getMaxPage();
    }
    if (page === currentPage) {
      return;
    }
    const newFrom = currentSize * page;
    if (newFrom < 0) {
      // first page
      this.setPage(0);
      return;
    }
    if (newFrom >= total) {
      // first page
      this.setPage(this.getMaxPage());
      return;
    }
    this.setState({ currentPage: page });
    if (paginationHandler) {
      paginationHandler(page, currentSize);
      // this.jumpTop(event);
    }
  }

  _changePage(event) {
    this.setState({
      changePage: event.currentTarget.value
    });
  }

  _submitChangedPage(event) {
    if (event) {
      event.preventDefault();
    }
    this.setPage(parseInt(this.state.changePage, 10) - 1);
  }

  _changeSize(event) {
    const { paginationHandler } = this.props;
    const size = parseInt(event.currentTarget.value, 10);
    this.setState({
      currentPage: 0,
      currentSize: size
    });
    if (paginationHandler) {
      paginationHandler(0, size);
    }
  }

  /**
   * Jump to page top
   */
  jumpTop() {
    // FIXME: doesn't work in modals => window  scroll is overidden with modal scroll
    $('html, body').animate({
      scrollTop: 0
    }, 'fast');
  }

  renderPagination() {
    const { total, paginationHandler } = this.props;
    const { currentPage, currentSize } = this.state;
    //
    if (!paginationHandler || total <= currentSize) {
      // if paginationHandler is not set, then its nothing to do
      return null;
    }
    // pages - max 5 pages
    const pages = [];
    let startPage = currentPage - 2;
    if ((this.getMaxPage() - 4) < startPage) {
      startPage = this.getMaxPage() - 4;
    }
    if (startPage < 0) {
      startPage = 0;
    }
    for (let page = startPage; page <= (this.getMaxPage()) && page < (startPage + 5); page++) {
      const active = currentPage === page;
      pages.push(
        <li key={ `page-${ page }` } className={ active ? 'active' : '' }>
          <a
            href="#"
            onClick={this.setPage.bind(this, page)}
            title={ this.i18n(active ? '' : 'component.basic.Table.Pagination.page.select', { page: page + 1 }) }>
            { page + 1 }
          </a>
        </li>
      );
    }
    //
    const prev = [];
    const activePrev = this.hasPrev();
    prev.push(
      <li key="page-first" className={ activePrev ? '' : 'disabled' }>
        <a ref="page-first" href="#" aria-label="First" onClick={ this.setPage.bind(this, 0) }>
          <span aria-hidden="true">&laquo;&laquo;</span>
        </a>
      </li>
    );
    prev.push(
      <li key="page-prev" className={ activePrev ? '' : 'disabled' }>
        <a ref="page-prev" href="#" aria-label="Previous" onClick={this.prev.bind(this)}>
          <span aria-hidden="true">&laquo;</span>
        </a>
      </li>
    );
    //
    const next = [];
    const activeNext = this.hasNext();
    next.push(
      <li key="page-next" className={activeNext ? '' : 'disabled'}>
        <a ref="page-next" href="#" aria-label="Next" onClick={this.next.bind(this)}>
          <span aria-hidden="true">&raquo;</span>
        </a>
      </li>
    );
    next.push(
      <li key="page-last" className={activeNext ? '' : 'disabled'}>
        <a ref="page-last" href="#" aria-label="Last" onClick={this.setPage.bind(this, this.getMaxPage())}>
          <span aria-hidden="true">&raquo;&raquo;</span>
        </a>
      </li>
    );

    return (
      <div className="text-center">
        <nav>
          <ul className="pagination pagination-sm" style={{ margin: 0 }}>
            {prev}
            {pages}
            {next}
          </ul>
        </nav>
      </div>
    );
  }

  renderPages() {
    const { total, paginationHandler, classes } = this.props;
    const { changePage, currentSize } = this.state;
    const maxPage = this.getMaxPage();
    if (!paginationHandler || !maxPage || total <= currentSize) {
      return null;
    }
    //
    return (
      <form onSubmit={ this._submitChangedPage.bind(this) }>
        { this.i18n('component.basic.Table.Pagination.page.title') }
        <input
          type="text"
          className={
            classNames(
              'form-control',
              classes ? classes.input : null
            )
          }
          value={ changePage }
          onChange={ this._changePage.bind(this) }/>
        { `${ this.i18n('component.basic.Table.Pagination.from')} ${ maxPage + 1 }` }
      </form>
    );
  }

  renderRecords() {
    const { total, paginationHandler, sizeOptions, showPageSize, classes } = this.props;
    const { currentPage, currentSize } = this.state;
    //
    let minRecord = 0;
    let maxRecord = 0;
    let totalRecords = 0;
    if (total) {
      totalRecords = total;
      minRecord = (currentPage * currentSize) + 1;
      if (!paginationHandler) {
        maxRecord = total;
      } else {
        maxRecord = (currentPage * currentSize) + currentSize;
      }
      if (maxRecord > total) {
        maxRecord = total;
      }
    }
    const sizes = [...sizeOptions.map(availableSize => (
      <option key={ `size-${ availableSize }` } value={ availableSize }>{ availableSize }</option>
    )).values()];
    //
    return (
      <div>
        <div className="record-info">
          { this.i18n('component.basic.Table.Pagination.recordInfo', { minRecord, maxRecord, totalRecords, escape: false }) }
        </div>
        {
          totalRecords && paginationHandler && sizeOptions[0] < totalRecords && showPageSize
          ?
          <div>
            { this.i18n('component.basic.Table.Pagination.size') }
            <select
              value={ currentSize }
              onChange={ this._changeSize.bind(this) }
              className={
                classNames(
                  classes ? classes.select : null
                )
              }>
              { sizes }
            </select>
          </div>
          :
          null
        }
      </div>
    );
  }

  render() {
    const { paginationHandler, classes } = this.props;
    const pages = this.renderPages();
    const pagination = this.renderPagination();
    const records = this.renderRecords();

    //
    return (
      <div
        className={
          classNames(
            'basic-pagination',
            classes ? classes.root : null
          )
        }
        style={{ display: 'block' }}>
        {
          paginationHandler
          ?
          <div className="row">
            <div className="col-sm-3 text-left">
              { pages }
            </div>
            <div className="col-sm-6">
              { pagination }
            </div>
            <div className="col-sm-3 text-right">
              { records }
            </div>
          </div>
          :
          <div>
            { records }
          </div>
        }
      </div>
    );
  }
}

Pagination.propTypes = {
  /**
   * Total records count
   */
  total: PropTypes.number,
  /**
   * Current page
   */
  page: PropTypes.number,
  /**
   * Page size
   */
  size: PropTypes.number,
  /**
   * Callback action for data pagination

   * @param number page
   * @param number size
   */
  paginationHandler: PropTypes.func,
  /**
   * Available Page sizes
   */
  sizeOptions: PropTypes.arrayOf(PropTypes.number),
  showPageSize: PropTypes.bool
};

Pagination.defaultProps = {
  total: null,
  page: 0,
  size: SearchParameters.getDefaultSize(),
  paginationHandler: null,
  sizeOptions: [10, 25, 50, 100],
  showPageSize: true
};

export default withStyles(styles, { withTheme: true })(Pagination);
