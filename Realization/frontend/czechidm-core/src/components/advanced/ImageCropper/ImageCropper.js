import React from 'react';
import PropTypes from 'prop-types';
import Cropper from 'react-cropper';
//
import ButtonGroup from '@material-ui/core/ButtonGroup';
//
import * as Basic from '../../basic';
import Well from '../../basic/Well/Well';

/**
* Component for image crop.
*
* @author Petr Hanák
* @author Radek Tomiška
*/
export default class ImageCropper extends Basic.AbstractContextComponent {

  constructor(props, context) {
    super(props, context);
    this.state = {
      src: null
    };
    this.cropperRef = React.createRef();
  }

  setDragMode(option) {
    this._getCropper().setDragMode(option);
  }

  reset() {
    this._getCropper().reset();
  }

  clear() {
    this._getCropper().clear();
  }

  rotateLeft() {
    this._getCropper().rotate(-90);
  }

  rotateRight() {
    this._getCropper().rotate(90);
  }

  zoomIn() {
    this._getCropper().zoom(0.1);
  }

  zoomOut() {
    this._getCropper().zoom(-0.1);
  }

  crop(cb) {
    const canvas = this._getCropper().getCroppedCanvas({
      width: 300, height: 300
    });
    //
    if (!canvas) {
      this.addMessage({ level: 'warning', message: this.i18n('error.IDENTITYIMAGE_WRONG_FORMAT.message') });
    } else if (canvas.toBlob !== undefined) {
      canvas.toBlob((blob) => {
        const formData = new FormData();
        formData.append('data', blob);
        cb(formData, blob);
      });
    } else if (canvas.msToBlob !== undefined) {
      const formData = new FormData();
      const msBlob = canvas.msToBlob();
      formData.append('data', msBlob);
      cb(formData, msBlob);
    } else {
      // TODO: manually convert Data-URI to Blob for older browsers https://developer.mozilla.org/en-US/docs/Web/API/HTMLCanvasElement/toBlob#Browser_compatibility
      LOGGER.error('[ImageCropper]: toBlog polyfill is not available');
    }
  }

  _getCropper() {
    return this.cropperRef.current.cropper;
  }

  render() {
    const { showLoading, rendered, src, width, height, fixedAspectRatio, autoCropArea } = this.props;
    //
    if (!rendered) {
      return null;
    }
    if (showLoading) {
      return <Well showLoading/>;
    }
    //
    return (
      <Basic.Div>
        <Cropper
          ref={ this.cropperRef }
          src={ src }
          viewMode={ 2 }
          dragMode="move"
          style={{ maxHeight: 568 }}
          autoCropArea={ autoCropArea }
          aspectRatio={ fixedAspectRatio ? (width / height) : undefined }
          initialAspectRatio={ fixedAspectRatio ? undefined : (width / height) } />

        <ButtonGroup
          color="primary"
          aria-label="contained button group"
          role="group"
          style={{
            padding: 10,
            position: 'absolute',
            bottom: 20,
            left: '50%',
            transform: 'translateX(-50%)'
          }} >
          <Basic.Button
            type="button"
            level="info"
            variant="contained"
            onClick={ this.setDragMode.bind(this, 'move') }>
            <Basic.Icon type="fa" icon="arrows" />
          </Basic.Button>
          <Basic.Button
            type="button"
            variant="contained"
            level="info"
            onClick={ this.setDragMode.bind(this, 'crop') }>
            <Basic.Icon type="fa" icon="crop" />
          </Basic.Button>
          <Basic.Button
            type="button"
            variant="contained"
            level="info"
            onClick={ this.zoomIn.bind(this) }>
            <Basic.Icon type="fa" icon="search-plus" />
          </Basic.Button>
          <Basic.Button
            type="button"
            variant="contained"
            level="info"
            onClick={ this.zoomOut.bind(this) }>
            <Basic.Icon type="fa" icon="search-minus" />
          </Basic.Button>
          <Basic.Button
            type="button"
            variant="contained"
            level="info"
            onClick={ this.rotateLeft.bind(this) }>
            <Basic.Icon type="fa" icon="rotate-left" />
          </Basic.Button>
          <Basic.Button
            type="button"
            variant="contained"
            level="info"
            onClick={ this.rotateRight.bind(this) }>
            <Basic.Icon type="fa" icon="rotate-right" />
          </Basic.Button>
          <Basic.Button
            type="button"
            variant="contained"
            level="info"
            onClick={ this.reset.bind(this) }>
            <Basic.Icon type="fa" icon="reply-all" />
          </Basic.Button>
        </ButtonGroup>
      </Basic.Div>
    );
  }
}

ImageCropper.PropTypes = {
  /**
  * Rendered component
  */
  rendered: PropTypes.bool,
  /**
  * Show loading in component
  */
  showLoading: PropTypes.bool,
  /**
   * It should be a number between 0 and 1. Define the automatic cropping area size (percentage).
   *
   * @since 12.0.0
   */
  autoCropArea: PropTypes.number,
  /**
   * Free or fixed crop box ratio.
   *
   * @since 12.0.0
   */
  fixedAspectRatio: PropTypes.bool,
  /**
   * Crop box ratio width.
   *
   * @since 12.0.0
   */
  width: PropTypes.number,
  /**
   * Crop box ratio height.
   *
   * @since 12.0.0
   */
  height: PropTypes.number
};

ImageCropper.defaultProps = {
  rendered: true,
  showLoading: false,
  autoCropArea: 0.6,
  fixedAspectRatio: true,
  width: 300,
  height: 300
};
