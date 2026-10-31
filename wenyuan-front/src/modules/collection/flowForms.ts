import { registerFlowForm } from '../flow/formRegistryCore';
import CollectionFlowForm from './forms/CollectionFlowForm.vue';
import ExhibitionFlowForm from './forms/ExhibitionFlowForm.vue';

registerFlowForm('COLLECTION_ACQUISITION', CollectionFlowForm);
registerFlowForm('COLLECTION_ACQUISITION_PLAN', CollectionFlowForm);
import RequisitionFlowForm from './forms/RequisitionFlowForm.vue';
registerFlowForm('COLLECTION_REQUISITION', RequisitionFlowForm);
registerFlowForm('COLLECTION_EXHIBITION', ExhibitionFlowForm);
import DeregistrationFlowForm from './forms/DeregistrationFlowForm.vue';
registerFlowForm('COLLECTION_DEREGISTER', DeregistrationFlowForm);
registerFlowForm('COLLECTION_APPRAISAL', CollectionFlowForm);
import RestorationDraftFlowForm from '../collection-restoration/components/RestorationDraftFlowForm.vue';
registerFlowForm('COLLECTION_RESTORATION', RestorationDraftFlowForm);
import LoanFlowForm from './forms/LoanFlowForm.vue';
registerFlowForm('COLLECTION_LOAN', LoanFlowForm);
import ReturnFlowForm from './forms/ReturnFlowForm.vue';
registerFlowForm('COLLECTION_RETURN', ReturnFlowForm);
registerFlowForm('COLLECTION_REPRODUCTION', CollectionFlowForm);
registerFlowForm('COLLECTION_INVENTORY', CollectionFlowForm);
import CollectionTransferFlowForm from './forms/CollectionTransferFlowForm.vue';
registerFlowForm('COLLECTION_TRANSFER', CollectionTransferFlowForm);
import WarehouseEntryFlowForm from './forms/WarehouseEntryFlowForm.vue';
registerFlowForm('COLLECTION_WAREHOUSE_ENTRY', WarehouseEntryFlowForm);

import CollectionChangeFlowForm from './forms/CollectionChangeFlowForm.vue';
registerFlowForm('COLLECTION_CHANGE', CollectionChangeFlowForm);

import SamplingFlowForm from './forms/SamplingFlowForm.vue';
registerFlowForm('COLLECTION_SAMPLING', SamplingFlowForm);
