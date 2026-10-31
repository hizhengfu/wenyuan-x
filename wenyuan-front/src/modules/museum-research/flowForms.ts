import { registerFlowForm } from '../flow/formRegistryCore';
import ResearchFlowForm from './forms/ResearchFlowForm.vue';

registerFlowForm('RESEARCH_TOPIC', ResearchFlowForm);
registerFlowForm('RESEARCH_ACHIEVEMENT', ResearchFlowForm);
