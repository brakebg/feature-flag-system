import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../../api/ApiError';
import { EmptyState } from '../../components/EmptyState';
import { useGroup, useGroups } from '../../hooks/queries';
import { loadErrorText } from '../../lib/errors';
import { NewGroupDialog } from './GroupDialog';
import { GroupList } from './GroupList';
import { GroupPanel } from './GroupPanel';
import styles from './GroupsPage.module.css';

/** Spec 8.4 flags workspace: groups on the left, the selected group (from the URL) on the right. */
export function GroupsPage() {
  const { groupId } = useParams();
  const navigate = useNavigate();
  const groups = useGroups();
  const group = useGroup(groupId);
  const [creating, setCreating] = useState(false);

  const unknown =
    group.error instanceof ApiError && (group.error.status === 404 || group.error.status === 400);
  let right;
  if (groupId && group.data && !unknown) {
    right = (
      <GroupPanel key={group.data.id} group={group.data} onDeleted={() => navigate('/groups')} />
    );
  } else if (groupId && group.isPending) {
    right = <main className={styles.placeholder} aria-busy="true" />;
  } else if (groupId && group.isError && !unknown) {
    right = (
      <main className={styles.placeholder}>
        <p role="alert" className={styles.error}>
          {loadErrorText(group.error, 'Could not load the group')}
        </p>
      </main>
    );
  } else {
    right = (
      <main className={styles.placeholder}>
        <EmptyState text="Select a group or create one" />
      </main>
    );
  }

  return (
    <div className={styles.grid}>
      <GroupList
        groups={groups.data}
        error={groups.isError ? loadErrorText(groups.error, 'Could not load groups') : undefined}
        selectedId={groupId}
        onSelect={(id) => navigate(`/groups/${id}`)}
        onNew={() => setCreating(true)}
      />
      {right}
      {creating && (
        <NewGroupDialog
          onClose={() => setCreating(false)}
          onCreated={(id) => {
            setCreating(false);
            navigate(`/groups/${id}`);
          }}
        />
      )}
    </div>
  );
}
